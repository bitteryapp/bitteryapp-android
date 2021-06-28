#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/android/jni_android.h"
#include "base/android/base_jni_onload.h"
#include "base/android/jni_string.h"
#include "base/strings/string_split.h"
#include "base/strings/string_util.h"
#include "base/strings/utf_string_conversions.h"
#include "base/task/post_task.h"
#include "base/task/thread_pool.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/thread_pool_instance.h"
#include "base/android/build_info.h"
#include "base/rand_util.h"
#include "btc/ecc.h"
#include "btc/ecc_key.h"
#include "btc/memory.h"
#include "btc/base58.h"
#include "btc/cstr.h"
#include "btc/utils.h"
#include "btc/tool.h"
#include "btc/segwit_addr.h"
#include "btc/script.h"
#include "btc/utils.h"
#include "btc/random.h"

#include "components/qr_code_generator/qr_code_generator.h"
#include "ui/gfx/android/java_bitmap.h"
#include "ui/gfx/canvas.h"
#include "ui/gfx/geometry/size.h"

#include "btc_richlist.h"

#include "bittery/bitterycore_jni/BitteryCore_jni.h"

#define EVENT_LUCKY_START 0x00001
#define EVENT_LUCKY_END   0x00002
#define EVENT_LUCKY_PROGRESS   0x00003

using base::android::JavaParamRef;
using base::android::ScopedJavaLocalRef;
using base::android::ConvertJavaStringToUTF8;
using base::android::ConvertJavaStringToUTF16;
using base::android::ConvertUTF8ToJavaString;
using namespace std;

struct btc_richitem {
  int balance;
  unsigned long magic_key;
  string addr;
  string key;
  bool ignore;
};

std::vector<btc_richitem> richlists;
std::vector<btc_richitem> luckylists;
static int g_match_num = 0;
static const int kModuleSizePixels = 8;
static bool g_richlist_sorted = false;

static bool compare_magic_key(const btc_richitem& a, const btc_richitem& b) {
  return a.magic_key < b.magic_key;
}

static unsigned long addr_to_magic_key(const char* addr) {
  const char* ptr = addr + 1;
  unsigned long key = 0;
  //int len = 10;
  int len = 4;

  if(strncmp(addr, "bc1", 3) == 0) {
    ptr = addr + 3;
  }

  while(len > 0) {
    key = (key + (*ptr-'0')) * 75;
    len--, ptr++;
  };

  return key;
}

static bool load_richitem(std::string& line, btc_richitem& r, const btc_chainparams* chain) {
  const char* ln = line.c_str();
  const char* ptr = strchr(ln, ' ');

  if(ptr != NULL) {
    int addrlen = ptr - ln;
    r.balance = atoi(ptr+1);
    r.addr = line.substr(0, addrlen);
    r.magic_key = addr_to_magic_key(r.addr.c_str());
    r.ignore = false;
    return true;
  }

  return false;
}

static void load_btc_richlist() {
  istringstream iss(BTC_RICHLIST);
  string line;

  while (getline(iss, line)) {
    btc_richitem r;
    if(load_richitem(line, r, &btc_chainparams_main)) {
      richlists.push_back(r);
    }
  }

  //for(auto r: richlists) {
  //  LOG(INFO) << "richlists.magic_key " << r.magic_key  << " addr " << r.addr;
  //}
}

static void _set_selection(JNIEnv* env, jclass jcaller, jstring j_selection) {
  string selection(ConvertJavaStringToUTF8(env, j_selection));
  for(unsigned int i = 0; i < selection.length(); i++) {
    if(selection[i] == '0') {
      richlists[i].ignore = true;
    }
  }

  if(g_richlist_sorted == false) {
    sort(richlists.begin(), richlists.end(), compare_magic_key);
    g_richlist_sorted = true;
  }
}

static void _publish_message(JNIEnv* env, jclass jcaller, jstring j_message) {
  string message(ConvertJavaStringToUTF8(env, j_message));
}

static void _init_bittery_core(JNIEnv* env, jclass jcaller) {
  btc_ecc_start();
  load_btc_richlist();
}

static int _get_match_num(string myaddr, string richaddr, int* score) {
  int match = 0, nskip = 1;
  const char* p = myaddr.c_str(), *q = richaddr.c_str();
  int np = myaddr.size(), nq = richaddr.size();

  if(*p != *q) return 0;
  if(strncmp(p, "bc1", 3) == 0) {
    nskip = 3;
  }
  p += nskip, q += nskip;

  if(*p == *q && np == nq) {
    if(myaddr == richaddr) return 100;

    while(*p++ == *q++) match ++;
    //score = (match*100)/myaddr.size();
    *score = (match * 100)/(nq - nskip);
    LOG(INFO) << "_get_match_num " << myaddr << " rich " << richaddr << " match " << match;
  }

  return match + nskip;
}

static void* do_lucky_attack_thread(void* data) {
  int tm = time(NULL), keycnt = 0, luckyscore = 0, richidx = -1;
  unsigned long newidx = 0, myidx = 0, luckyidx = 0;
  size_t strsize_wif = 128;
  btc_key key = {0};
  btc_pubkey pubkey = {0};
  long sec = (long)data, deltatm = 0;

  JNIEnv* env = base::android::AttachCurrentThreadWithName("bittery");
  ScopedJavaLocalRef<jstring> empty = ConvertUTF8ToJavaString(env, "");
  Java_BitteryCore_pushMessage(env, EVENT_LUCKY_START, 0, 0, empty);

  do {
    btc_richitem ri = {0};
    char privkey_wif[128] = {0};
    char privkey_hex[128] = {0};
    char address_p2pkh[128] = {0};
    char address_p2sh_p2wpkh[128] = {0};
    char address_p2wpkh[128] = {0};

    btc_privkey_init(&key);
    btc_privkey_gen(&key);
    btc_privkey_encode_wif(&key, &btc_chainparams_main, privkey_wif, &strsize_wif);
    //utils_bin_to_hex(key.privkey, BTC_ECKEY_PKEY_LENGTH, privkey_hex);

    btc_pubkey_init(&pubkey);
    btc_pubkey_from_key(&key, &pubkey);

    btc_pubkey_getaddr_p2pkh(&pubkey, &btc_chainparams_main, address_p2pkh);
    btc_pubkey_getaddr_p2sh_p2wpkh(&pubkey, &btc_chainparams_main, address_p2sh_p2wpkh);
    btc_pubkey_getaddr_p2wpkh(&pubkey, &btc_chainparams_main, address_p2wpkh);
    //LOG(INFO) << "WIF:" << privkey_wif << " p2pkh address: " << address_p2pkh;
    //LOG(INFO) << "WIF:" << privkey_wif << " p2sh-p2wpkh address: " << address_p2sh_p2wpkh;
    //LOG(INFO) << "WIF:" << privkey_wif << " p2wpkh (bc1 / bech32) address: " << address_p2wpkh;

    btc_privkey_cleanse(&key);
    keycnt++;

    ri.addr = string(address_p2pkh);
    ri.key  = string(privkey_wif);
    ri.magic_key = addr_to_magic_key(address_p2pkh);
    luckylists.push_back(ri);

    ri.addr = string(address_p2sh_p2wpkh);
    ri.key  = string(privkey_wif);
    ri.magic_key = addr_to_magic_key(address_p2sh_p2wpkh);
    luckylists.push_back(ri);

    ri.addr = string(address_p2wpkh);
    ri.key  = string(privkey_wif);
    ri.magic_key = addr_to_magic_key(address_p2wpkh);
    luckylists.push_back(ri);
    if(time(NULL) - tm > deltatm) {
      deltatm = time(NULL) - tm;
      Java_BitteryCore_pushMessage(env, EVENT_LUCKY_PROGRESS, deltatm, sec, empty);
    }
  } while(time(NULL) - tm <= sec);
  sort(luckylists.begin(), luckylists.end(), compare_magic_key);

  int bstm = time(NULL);
  for(myidx = 0 ; myidx < luckylists.size(); myidx ++) {
    if(luckylists[myidx].magic_key < richlists[0].magic_key) {
      continue;
    }

    unsigned long l = newidx, h = richlists.size()-1;
    while(l <= h) {
      int m = (l+h) / 2;
      if(richlists[m].magic_key == luckylists[myidx].magic_key) {
        int score = 0;
        int match = _get_match_num(luckylists[myidx].addr, richlists[m].addr, &score);
        if(score > 0 && false == richlists[m].ignore) {
          if(score > luckyscore) {
            luckyscore = score;
            luckyidx = myidx;
            richidx = m;
            g_match_num = match;
            //LOG(INFO) << "luckyscore " << luckyscore << " score " << score;
            //LOG(INFO) << "luckyitem addr:" << luckylists[myidx].addr << " key:" << luckylists[myidx].key;
            //LOG(INFO) << "RICHLISTS addr:" << richlists[m].addr;
          }
        }
        break;
      } else if(richlists[m].magic_key < luckylists[myidx].magic_key) {
        l = m + 1;
      } else {
        h = m -1;
      }
    }

    //for( ; newidx < richlists.size(); newidx ++) {
    //  if(richlists[newidx].magic_key >= r.magic_key)
    //    break;
    //}
  }
  //LOG(INFO) << "binary_search time " << time(NULL) - bstm << "sec, keycnt:" << keycnt;

  char rich_info_cstr[128] = {0};
  snprintf(rich_info_cstr, 128, "%s %dBTC", luckylists[luckyidx].addr.c_str(), richlists[richidx].balance);
  ScopedJavaLocalRef<jstring> richinfo = ConvertUTF8ToJavaString(env, rich_info_cstr);
  Java_BitteryCore_pushMessage(env, EVENT_LUCKY_END, luckyidx, richidx, richinfo);
  return data;
}

static jstring _get_lucky_addr(JNIEnv* env, jclass jcaller, jint luckyidx) {
  return env->NewStringUTF(luckylists[luckyidx].addr.c_str());
}

static jstring _get_lucky_priv(JNIEnv* env, jclass jcaller, jint luckyidx) {
  return env->NewStringUTF(luckylists[luckyidx].key.c_str());
}

static jobject _get_qr_bitmap(JNIEnv* env, jclass jcaller, jstring j_key) {
  QRCodeGenerator qr;
  string key(ConvertJavaStringToUTF8(env, j_key));
  base::Optional<QRCodeGenerator::GeneratedCode> qr_data = qr.Generate(base::span<const uint8_t>(reinterpret_cast<const uint8_t*>(key.c_str()), key.length()));
  if (!qr_data || qr_data->data.data() == nullptr || qr_data->data.size() == 0) {
    LOG(INFO) << "qr.Generate " << key << " ERROR ";
  } else {
    SkBitmap bitmap;
    auto& qr_data_span = qr_data->data;
    LOG(INFO) << "_get_qr_bitmap " << qr_data_span.data() << " data_size " << qr_data->qr_size;

    bitmap.allocN32Pixels(qr_data->qr_size * kModuleSizePixels, qr_data->qr_size * kModuleSizePixels);
    bitmap.eraseARGB(0xFF, 0xFF, 0xFF, 0xFF);
    SkCanvas canvas(bitmap, SkSurfaceProps{});
    SkPaint paint_black;
    paint_black.setColor(SK_ColorBLACK);
    SkPaint paint_white;
    paint_white.setColor(SK_ColorWHITE);

    int data_index = 0;
    uint8_t* qrptr = qr_data_span.data();
    for (int y = 0; y < qr_data->qr_size; y++) {
      for (int x = 0; x < qr_data->qr_size; x++) {
        if (qrptr[data_index++] & 0x1) {
          //bool is_locator =
          //    (y <= kLocatorSizeModules &&
          //     (x <= kLocatorSizeModules ||
          //      x >= data_size.width() - kLocatorSizeModules - 1)) ||
          //    (y >= data_size.height() - kLocatorSizeModules - 1 &&
          //     x <= kLocatorSizeModules);
          //if (is_locator) {
          //  continue;
          //}

          //if (request->render_module_style == mojom::ModuleStyle::CIRCLES) {
          //  float xc = (x + 0.5) * kModuleSizePixels;
          //  float yc = (y + 0.5) * kModuleSizePixels;
          //  SkScalar radius = kModuleSizePixels / 2 - 1;
          //  canvas.drawCircle(xc, yc, radius, paint_black);
          //} else {
            canvas.drawRect(
                {x * kModuleSizePixels, y * kModuleSizePixels,
                 (x + 1) * kModuleSizePixels, (y + 1) * kModuleSizePixels},
                paint_black);
          //}
        }
      }
    }

    return gfx::ConvertToJavaBitmap(bitmap, gfx::OomBehavior::kReturnNullOnOom).Release();
  }
  return NULL;
}

static jstring _get_rich_addr (JNIEnv* env, jclass jcaller, jint richidx) {
  return env->NewStringUTF(richlists[richidx].addr.c_str());
}

static jint _get_rich_balance(JNIEnv* env, jclass jcaller, jint richidx) {
  return richlists[richidx].balance;
}


static void _lucky_shake(JNIEnv* env, jclass jcaller, jint sec) {
  if(g_richlist_sorted) {
    pthread_t thread_id;
    luckylists.clear();
    pthread_create(&thread_id, NULL, do_lucky_attack_thread, (void *)sec);
  }
}

static jint _jni_get_match_num(JNIEnv* env, jclass jcaller) {
  return g_match_num;
}

static const char* clazzName = "com/github/bitteryapp/BitteryCore";
static JNINativeMethod methods[] = {
  { "initBitteryCore", "()V", (void *)_init_bittery_core },
  { "luckyShake", "(I)V", (void *)_lucky_shake },
  { "publishMessage", "(Ljava/lang/String;)V", (void *)_publish_message },
  { "getQRBitmap", "(Ljava/lang/String;)Landroid/graphics/Bitmap;", (void *)_get_qr_bitmap },
  { "getLuckyAddr", "(I)Ljava/lang/String;", (void *)_get_lucky_addr },
  { "getLuckyPriv", "(I)Ljava/lang/String;", (void *)_get_lucky_priv },
  { "getRichAddr", "(I)Ljava/lang/String;", (void *)_get_rich_addr },
  { "getRichBalance", "(I)I", (void *)_get_rich_balance },
  { "setSelection", "(Ljava/lang/String;)V", (void *)_set_selection },
  { "getMatchNum", "()I", (void *) _jni_get_match_num },
};

static int registerNative(JNIEnv* env) {
  jclass clazz = env->FindClass(clazzName);
  jint status = JNI_FALSE;
  if(clazz != NULL) {
    if(env->RegisterNatives(clazz, methods, sizeof(methods)/sizeof(methods[0])) >= 0) {
      status = JNI_TRUE;
    }
  }
  return status;
}

JNI_EXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  base::android::InitVM(vm);

  if (!base::android::OnJNIOnLoadInit())
    return false;

  JNIEnv* env = base::android::AttachCurrentThread();
  if(registerNative(env) != JNI_TRUE) {
  }

  base::ThreadPoolInstance::CreateAndStartWithDefaultParams("bittery");
  return JNI_VERSION_1_6;
}
