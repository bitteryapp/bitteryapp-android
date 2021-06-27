#!/usr/bin/python3
# parse content from https://bitinfocharts.com/top-100-richest-bitcoin-addresses-1.html

file = open('/tmp/btc_richlist', 'r')
check1_passed = True
check2_passed = True
for line in file:
  info = line.split()
  if line.find("BTC") == -1:
    print("CHECK1 {} ".format(info[1]))
    check1_passed = False
file.close()

if check1_passed :
  file = open('/tmp/btc_richlist', 'r')
  for line in file:
    info = line.split()
    if len(info[1]) < 30 :
      print("CHECK2 {} ".format(info[1]))
      check2_passed = False
  file.close()

if check2_passed :
  file = open('/tmp/btc_richlist', 'r')
  print('#ifndef __BTC_RICHLIST__\n#define __BTC_RICHLIST__\nconst char* BTC_RICHLIST =\nR\"(')
  for line in file:
    info = line.split()
    balance = ""
    for item in info :
      if item == 'BTC' :
        break
      balance = item.replace(',', '')
    print("{} {}".format(info[1], balance))
  print(")\";\n#endif")
  file.close()
