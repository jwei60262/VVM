package com.att.mobile.android.vvm.model.db.inbox;

import android.database.Cursor;

import com.att.mobile.android.vvm.model.inbox.ListItemCursorRecyclerAdapterBase;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.util.ArrayList;

/**
 * Created by drosenfeld on 01/03/2016.
 */
public class AggregatedVoicemailItem extends VoicemailItemBase{

    private int             newInboxMessagesCount;      //All new messages from this phone number (unread and unsaved)
    private int             oldInboxMessagesCount;     //All inbox messages related to this phone number
//    private ArrayList<Long> messagesID;             //List of all inbox message ID’s

    //Saved
//    private int             savedMessagesCount;     //All saved messages related to this phone number
//    private int             newSavedMessagesCount;  //All new saved messages from this phone number;
//    private ArrayList<Long> savedMessagesID;        //All saved message ID’s

    public AggregatedVoicemailItem (Cursor cursor) {
        //TODO Dana - implement
        super(cursor);
        oldInboxMessagesCount = cursor.getInt(ModelManager.IND_AGGREGATED_READ_COUNT);
        newInboxMessagesCount = cursor.getInt(ModelManager.IND_AGGREGATED_UNREAD_COUNT);
    }

    /**
     * @return the inbox messages count
     */
    public int getOldInboxMessagesCount() {
        return oldInboxMessagesCount;
    }

    /**
     * @return the new inbox messages count
     */
    public int getNewInboxMessagesCount() {
        return newInboxMessagesCount;
    }

    @Override
    public  void updateViewHolder(ListItemCursorRecyclerAdapterBase.ViewHolder viewHolder, Cursor cursor) {
        //TODO Dana: implement
    }
}
