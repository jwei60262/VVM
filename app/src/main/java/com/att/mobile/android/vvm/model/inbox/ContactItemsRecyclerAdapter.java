package com.att.mobile.android.vvm.model.inbox;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItem;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItemBase;

/**
 * Created by hginsburg on 3/29/2016.
 */
public class ContactItemsRecyclerAdapter extends VoicemailItemRecyclerAdapter {

    private static final String TAG = ContactItemsRecyclerAdapter.class.getSimpleName();

    public ContactItemsRecyclerAdapter(Context context, int filterType, Cursor c) {
        super(context, filterType, c);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_aggregated_item, parent, false);

        ListItemCursorRecyclerAdapterBase.ViewHolder vh = new ViewHolder(v);

        return vh;

    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {

        VoicemailItem voicemailItem = new VoicemailItem(cursor);
        Logger.i(TAG, "onBindViewHolder voicemailItem=" + voicemailItem.toString());
        int position = cursor.getPosition();

        setEditModeItems(holder, voicemailItem);

        updateTranscription(holder, voicemailItem);
        updateDate(holder, voicemailItem);

        boolean readState = isReadListItem(voicemailItem);
        updateUIAttributesAccordingToReadState(holder, readState);

        holder.parent.setOnClickListener(new ItemClickListener(voicemailItem, position));
        holder.parent.setOnLongClickListener(new ItemLongClickListener(voicemailItem));

    }

    @Override
    protected void setEditModeItems(ViewHolder holder, VoicemailItemBase voicemailItem) {

        holder.selectedImage.setVisibility( mIsActionModeActive ? View.VISIBLE : View.GONE );
        if ( isSelectedItem(voicemailItem) ) {
            holder.selectedImage.setBackgroundResource(R.drawable.gray_circle);
            holder.selectedImage.setImageResource(R.drawable.ic_action_done);
            holder.parent.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray_light3));
        } else {
            holder.selectedImage.setBackgroundResource(R.drawable.gray_circle_hollow);
            holder.selectedImage.setImageDrawable(null);
            holder.parent.setBackgroundColor(Color.WHITE);
        }
    }
}
