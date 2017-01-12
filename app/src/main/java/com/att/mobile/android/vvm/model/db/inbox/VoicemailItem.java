package com.att.mobile.android.vvm.model.db.inbox;

import android.database.Cursor;

import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.inbox.ListItemCursorRecyclerAdapterBase;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * Created by drosenfeld on 01/03/2016.
 */
public class VoicemailItem extends VoicemailItemBase {

    private long time;
    private String transcription;
    private String fileName;
    private int savedState;
    private String forwardState;
    private int readState;
    private boolean isCheckedForDelete;

    private boolean deliveryFailure;
    private boolean isUrgent;


    public VoicemailItem (Cursor cursor) {

        super(cursor);
        // gets message's ID
        time = cursor.getLong(cursor.getColumnIndex(ModelManager.Inbox.KEY_TIME_STAMP));

        transcription = cursor.getString(cursor.getColumnIndex(ModelManager.Inbox.KEY_TRANSCRIPTION));

        readState = cursor.getInt(cursor.getColumnIndex((ModelManager.Inbox.KEY_IS_READ)));
        savedState = cursor.getInt(cursor.getColumnIndex(ModelManager.Inbox.KEY_SAVED_STATE));

        isUrgent = (cursor.getInt(cursor.getColumnIndex(ModelManager.Inbox.KEY_URGENT_STATUS)) == 1);
        deliveryFailure = (cursor.getInt(cursor.getColumnIndex(ModelManager.Inbox.KEY_SAVED_STATE)) == Message.SavedStates.ERROR);
        fileName = (cursor.getString(cursor.getColumnIndex(ModelManager.Inbox.KEY_FILE_NAME)));
    }

    public boolean isCheckedForDelete() {
        return isCheckedForDelete;
    }

    public void setCheckedForDelete(boolean isCheckedForDelete) {
        this.isCheckedForDelete = isCheckedForDelete;
    }

    /**
     * @param time the time to set
     */
    public void setTime(long time) {
        this.time = time;
    }

    /**
     * @return the time
     */
    public long getTime() {
        return time;
    }

    /**
     * @param transcription the transcription to set
     */
    public void setTranscription(String transcription) {
        this.transcription = transcription;
    }

    /**
     * @return the transcription
     */
    public String getTranscription() {
        return transcription;
    }

    /**
     * @param fileName the fileName to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * @return the fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * @param savedState the savedState to set
     */
    public void setSavedState(int savedState) {
        this.savedState = savedState;
    }

    /**
     * @return the savedState
     */
    public int getSavedState() {
        return savedState;
    }

    /**
     * @param forwardState the forwardState to set
     */
    public void setForwardState(String forwardState) {
        this.forwardState = forwardState;
    }

    /**
     * @return the forwardState
     */
    public String getForwardState() {
        return forwardState;
    }

    /**
     * @param readState the readState to set
     */
    public void setReadState(int readState) {
        this.readState = readState;
    }

    /**
     * @return the readState
     */
    public int getReadState() {
        return readState;
    }

    /**
     * @param uid the uid to set
     */
    public void setUid(long uid) {
        this.uid = uid;
    }

    /**
     * @return the uid
     */
    public long getUid() {
        return uid;
    }

    /**
     * @param urgent the id to set
     */
    public void setUrgent(boolean urgent) {
        this.isUrgent = urgent;
    }

    /**
     * @return the urgency status
     */
    public boolean getIsUrgent() {
        return isUrgent;
    }

    /**
     * @param deliveryFailure the delivery failure state to set
     */
    public void setIsDeliveryFailure(boolean deliveryFailure) {
        this.deliveryFailure = deliveryFailure;
    }

    /**
     * @return the delivery failure status
     */
    public boolean getIsdeliveryFailure() {
        return deliveryFailure;
    }

    @Override
    public void updateViewHolder(ListItemCursorRecyclerAdapterBase.ViewHolder viewHolder, Cursor cursor) {

        super.updateViewHolder(viewHolder, cursor);
    }

    @Override
    public String toString() {
        return new StringBuilder().append("id=").append(id).append(" uid=").append(uid).append(" transcription=").append(transcription).append(" fileName=").append(fileName).
                append(" savedState=").append(savedState).toString();
    }
}