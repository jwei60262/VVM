package com.att.mobile.android.vvm.model.inbox;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.db.inbox.AggregatedVoicemailItem;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItemBase;

import java.util.ArrayList;


/**
 * Created by drosenfeld on 06/03/2016.
 */
public class AggregatedVoicemailItemRecyclerAdapter extends ListItemCursorRecyclerAdapterBase {

    private static final String TAG = AggregatedVoicemailItemRecyclerAdapter.class.getSimpleName();

    String new_str = null;
    String old_str = null;
    String message_str = null;
    String messages_str = null;

    private static final String SPACE = " ";

    protected ArrayList<String> selectedItems = new ArrayList<String>();

    @Override
    public boolean selectByIds() {
        return false;
    }

    @Override
    public String[] getSelectedPhonesItems() {
        return selectedItems.toArray( new String[selectedItems.size()] );
    }

    public AggregatedVoicemailItemRecyclerAdapter(Context context, int filterType, Cursor c) {

        super(context, filterType, c);
        new_str = context.getString(R.string.new_str);
        old_str = context.getString(R.string.old_str);
        message_str = context.getString(R.string.message_str);
        messages_str = context.getString(R.string.messages_str);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewHolder holder = super.onCreateViewHolder(parent, viewType);
        holder.date.setVisibility(View.GONE);
        holder.urgentStatus.setVisibility(View.GONE);
        holder.savedOrDownloadFlipper.setVisibility(View.GONE);
        return holder;
    }

    @Override
    public void onBindViewHolder(ListItemCursorRecyclerAdapterBase.ViewHolder holder, Cursor cursor) {

        AggregatedVoicemailItem voicemailItem = new AggregatedVoicemailItem(cursor);
        int position = cursor.getPosition();

        setEditModeItems(holder, voicemailItem);

        if ( !setDefaultMessage(holder, voicemailItem) ) {
            if ( !updatePhotoAndDisplayName(holder, voicemailItem, position) ) {
                setDefaultAvatarImage(holder, voicemailItem, false);
            }
        }
        updateTranscription(holder, voicemailItem);

        boolean isRead = voicemailItem.getNewInboxMessagesCount() == 0;
        updateUIAttributesAccordingToReadState(holder, isRead);

        holder.parent.setOnClickListener(new ItemClickListener(voicemailItem, position));
        holder.parent.setOnLongClickListener(new ItemLongClickListener(voicemailItem));
    }

    @Override
    protected boolean isSelectedItem(VoicemailItemBase item) {
        if ( item == null ) {
            return false;
        }
        return selectedItems.contains(item.getPhoneNumber());
    }

    @Override
    protected void toggleSelection(VoicemailItemBase item) {

        if ( selectedItems.contains(item.getPhoneNumber()) ) {
            selectedItems.remove(item.getPhoneNumber());
        } else {
            selectedItems.add(item.getPhoneNumber());
        }
        notifyDataSetChanged();
    }

    @Override
    protected int getSelectedCount() {
        return selectedItems.size();
    }

    @Override
    public void turnEditModeOff() {

        selectedItems.clear();
        super.turnEditModeOff();
    }

    protected void updateTranscription(ViewHolder holder, AggregatedVoicemailItem aggregatedVoicemailItem) {

        int oldVMCount = aggregatedVoicemailItem.getOldInboxMessagesCount();
        int newVMCount = aggregatedVoicemailItem.getNewInboxMessagesCount();
        holder.transcriptionText.setText(buildMessageCountDescription(newVMCount, oldVMCount));
        if ( newVMCount > 0 ) {
            holder.transcriptionText.setTypeface(null, Typeface.BOLD);
        }
    }

    private String buildMessageCountDescription ( int newMsgCount, int oldMsgCount ) {

        StringBuilder buf = new StringBuilder();
        if ( newMsgCount > 0 ) {
            buf.append(newMsgCount).append(SPACE).append(new_str).append(SPACE); // add 'new 2 '
            buf.append(newMsgCount > 1 ? messages_str : message_str);  // add 'message' or 'messages'
            if ( oldMsgCount > 0 ) {
                buf.append(", ");
            }
        }
        if ( oldMsgCount > 0 ) {
            buf.append(oldMsgCount).append(SPACE).append(old_str).append(SPACE); // add 'old 2'
            buf.append(oldMsgCount > 1 ? messages_str : message_str);  // add 'message' or 'messages'
        }
        Logger.i(TAG, "buildMessageCountDescription newMsgCount=" + newMsgCount + " oldMsgCount=" + oldMsgCount + " description=" + buf.toString());
        return buf.toString();
    }

}