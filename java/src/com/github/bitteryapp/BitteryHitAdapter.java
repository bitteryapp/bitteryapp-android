package com.github.bitteryapp;

import java.util.List;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import org.chromium.ui.widget.AnchoredPopupWindow;
import org.chromium.ui.widget.ViewRectProvider;
import android.view.LayoutInflater;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.AlertDialog;

public class BitteryHitAdapter extends RecyclerView.Adapter<BitteryHitAdapter.ViewHolder> {
    private static List<BitteryHit> mHits;

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        //public AppCompatImageView mHitIcon;
        public TextView mHitInfo;
        private Context mContext;
        // We also create a constructor that accepts the entire item row
        // and does the view lookups to find each subview
        public ViewHolder(View itemView, Context context) {
            // Stores the itemView in a public final member variable that can be used
            // to access the context from any ViewHolder instance.
            super(itemView);
            itemView.setOnClickListener(this);
            mHitInfo = (TextView)itemView.findViewById(R.id.hit_info);
            mContext = context;
        }

        @Override
        public void onClick(View v) {
            int npos = getAdapterPosition();
            BitteryHit hit = mHits.get(npos);
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            View h = (View)LayoutInflater.from(mContext).inflate(R.layout.bittery_key_info, null);
            ImageView qr = (ImageView)h.findViewById(R.id.qrkey);
            qr.setImageBitmap(hit.getKeyQR());
            TextView view = (TextView)h.findViewById(R.id.hit_info);
            view.setText(hit.getPrivKey());
            //ImageView scoreImageView = (ImageView)h.findViewById(R.id.score);
            //scoreImageView.setImageBitmap(hit.getScoreBitmap());

            TextView score = (TextView)h.findViewById(R.id.hit_score);
            score.setTextColor(Color.parseColor("#FF0000"));
            score.setText(Integer.toString(hit.getScore()));
            builder.setView(h).setTitle(hit.getBTCAddr()).setIcon(R.drawable.bitcoin_hit_icon); //.setPositiveButton(R.string.copy_key, null);
            AlertDialog dialog = builder.create();

            dialog.show();
        }
    }

    public BitteryHitAdapter(List<BitteryHit> hits) {
        mHits = hits;
    }

    @Override
    public BitteryHitAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // Inflate the custom layout
        View hitView = inflater.inflate(R.layout.bittery_hit_item, parent, false);

        // Return a new holder instance
        ViewHolder viewHolder = new ViewHolder(hitView, context);
        return viewHolder;
    }

    // Involves populating data into the item through holder
    @Override
    public void onBindViewHolder(BitteryHitAdapter.ViewHolder holder, int position) {
        // Get the data model based on position
        BitteryHit hit = mHits.get(position);
        int nMatch = hit.getMatchNum();
        SpannableString btcText = new SpannableString(hit.getBTCAddr());

        btcText.setSpan(new ForegroundColorSpan(Color.parseColor("#0F9D58")), 0, nMatch, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        btcText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, nMatch, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(nMatch < hit.getBTCAddr().length()) {
            btcText.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), nMatch + 1, hit.getBTCAddr().length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }

        TextView textView = holder.mHitInfo;
        textView.setText(btcText);
    }

    @Override
    public int getItemCount() {
        return mHits.size();
    }
}
