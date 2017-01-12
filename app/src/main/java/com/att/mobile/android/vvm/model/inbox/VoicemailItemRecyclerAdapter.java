package com.att.mobile.android.vvm.model.inbox;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.TimeDateUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItem;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItemBase;

import java.util.ArrayList;


/**
 * Created by drosenfeld on 06/03/2016.
 */
public class VoicemailItemRecyclerAdapter extends ListItemCursorRecyclerAdapterBase {

    private static final String TAG = VoicemailItemRecyclerAdapter.class.getSimpleName();

    /* holds a rotate animation for when row's data is being loaded */

    private RotateAnimation rotateAnimation;

    protected ArrayList<Long> selectedItems = new ArrayList<Long>();

    private static interface fileStatusDisplay {

        public static final int DOWNLOADING = 0;
        public static final int ERROR = 1;
    }

    public VoicemailItemRecyclerAdapter(Context context, int filterType, Cursor c) {
        super(context, filterType, c);

        initRotateAnimation();
    }

    private void initRotateAnimation() {
        rotateAnimation = new RotateAnimation(0.0f, 360.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(1200);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setRepeatCount(RotateAnimation.INFINITE);
    }

    @Override
    public void onBindViewHolder(ListItemCursorRecyclerAdapterBase.ViewHolder holder, Cursor cursor) {

        VoicemailItem voicemailItem = new VoicemailItem(cursor);
        Logger.i(TAG, "onBindViewHolder voicemailItem=" + voicemailItem.toString());
        int position = cursor.getPosition();

        setEditModeItems(holder, voicemailItem);

        if ( !setDefaultMessage(holder, voicemailItem) ) {
            if ( !updatePhotoAndDisplayName(holder, voicemailItem, position) ) {
                setDefaultAvatarImage(holder, voicemailItem, false);
            }
        }
        updateTranscription(holder, voicemailItem);
        updateDate(holder, voicemailItem);

        updateSaveOrDownloadFlipperAndAnimation(holder, voicemailItem);
        updateUrgentStatus(holder, voicemailItem);

        boolean readState = isReadListItem(voicemailItem);
        updateUIAttributesAccordingToReadState(holder, readState);

        holder.parent.setOnClickListener(new ItemClickListener(voicemailItem, position));
        holder.parent.setOnLongClickListener(new ItemLongClickListener(voicemailItem));
    }


    protected void updateTranscription(ViewHolder holder, VoicemailItem voicemailItem) {

        String transcription = voicemailItem.getTranscription();
        if(transcription == null || TextUtils.isEmpty(transcription.trim())){

            transcription = mContext.getString(R.string.noTranscriptionMessage);
        }
        holder.transcriptionText.setText(transcription);
    }


    protected void updateDate(ViewHolder holder, VoicemailItem voicemailItem) {

        String time = TimeDateUtils.getFriendlyDate(voicemailItem.getTime(), VVMApplication.getContext(), true);
        holder.date.setText(time);
    }

    protected boolean isReadListItem(VoicemailItem voicemailItem) {

        if (voicemailItem.getReadState() == Message.ReadDeletedState.READ) {
            return true;
        }
        return false;
    }

    protected void updateUrgentStatus(ViewHolder holder, VoicemailItem voicemailItem) {

        if (voicemailItem.getIsUrgent()) {
            holder.urgentStatus.setVisibility(View.VISIBLE);
        } else {
            holder.urgentStatus.setVisibility(View.GONE);
        }
    }


    protected void updateSaveOrDownloadFlipperAndAnimation(ViewHolder holder, VoicemailItem voicemailItem) {

        holder.savedOrDownloadFlipper.setVisibility(View.GONE);
        // if no file exist yet - set the flipper with the downloading animation.
        // else if message was saved set the view with saved icon, otherwise set with nothing

        boolean isError = (voicemailItem.getSavedState() == Message.SavedStates.ERROR);
        String fileName = voicemailItem.getFileName();

        if (fileName == null || fileName.length() == 0) {
            if (VVMApplication.isMemoryLow() || isError) {
                stopGauge(holder);
                holder.savedOrDownloadFlipper.setVisibility(View.VISIBLE);
                holder.savedOrDownloadFlipper.setDisplayedChild(fileStatusDisplay.ERROR);
            } else {
                holder.savedOrDownloadFlipper.setVisibility(View.VISIBLE);
                holder.savedOrDownloadFlipper.setDisplayedChild(fileStatusDisplay.DOWNLOADING);
                // start animation until download is finished
                // on download finished - cursor re query will be called in the inbox activity
                // to reset the adapter by getting back to this point and check again the file name
                startGauge(holder);
            }
        } else {
            // we have a file so stop the download animation if exists
            stopGauge(holder);
            // no need to show anything in this view
            holder.savedOrDownloadFlipper.setVisibility(View.GONE);
        }
   }

    private void startGauge(ViewHolder viewHolder) {
        viewHolder.downloadFileGauge = (ImageView) viewHolder.savedOrDownloadFlipper.findViewById(R.id.downloadFileImage);
        viewHolder.downloadFileGauge.setVisibility(View.VISIBLE);
        // starts gauge's animation
        viewHolder.downloadFileGauge.startAnimation(rotateAnimation);
    }

    /**
     * Stop waiting animation
     */
    private void stopGauge(ViewHolder viewHolder) {
        // close animation
        if (viewHolder.savedOrDownloadFlipper.getDisplayedChild() == 1 && viewHolder.downloadFileGauge != null) {
            viewHolder.downloadFileGauge.clearAnimation();
            viewHolder.downloadFileGauge.setVisibility(View.GONE);
            viewHolder.downloadFileGauge = null;
        }
        viewHolder.downloadFileGauge = null;
    }

    @Override
    protected boolean isSelectedItem(VoicemailItemBase item) {
        if ( item == null ) {
            return false;
        }
        return selectedItems.contains(item.getId());
    }

    @Override
    protected void toggleSelection(VoicemailItemBase item) {

        if ( selectedItems.contains(item.getId()) ) {
            selectedItems.remove(item.getId());
        } else {
            selectedItems.add(item.getId());
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

    @Override
    public Long[] getSelectedIdsItems() {
        return selectedItems.toArray(new Long[selectedItems.size()]);
    }
}
