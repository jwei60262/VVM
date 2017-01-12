package com.att.mobile.android.vvm.model.db.inbox;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;

import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.ContactObject;
import com.att.mobile.android.vvm.model.inbox.ListItemCursorRecyclerAdapterBase;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * Created by drosenfeld on 01/03/2016.
 */
public abstract class VoicemailItemBase {

    protected long id;
    protected long uid;
    protected String phoneNumber;
    protected ContactObject contact;

    VoicemailItemBase() {

    }

    public VoicemailItemBase (Cursor cursor) {

        this.id  = cursor.getLong(cursor.getColumnIndex(ModelManager.Inbox._ID));
        this.uid = cursor.getLong(cursor.getColumnIndex(ModelManager.Inbox.KEY_UID));
        setPhoneNumber(cursor.getString(cursor.getColumnIndex(ModelManager.Inbox.KEY_PHONE_NUMBER)));
    }

    public void updateViewHolder(ListItemCursorRecyclerAdapterBase.ViewHolder viewHolder, Cursor cursor) {

    }

    public long getId() {
        return id;
    }

    public long getUid() {
        return uid;
    }

    /**
     * @param contact the contact to set
     */
    public void setContact(ContactObject contact) {
        this.contact = contact;
    }

    /**
     * @return the phoneNumber
     */
    public ContactObject getContact() {
        return contact;
    }


    /**
     * @param phoneNumber the phoneNumber to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * @return the phoneNumber
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getFormattedDisplayName() {
        return Utils.getFormattedDisplayName(phoneNumber, contact);
    }

    @NonNull
    private String getPrivateNumberString() {
        return VVMApplication.getContext().getString(R.string.privateNumber);
    }

    @Override
    public String toString() {
        return new StringBuilder().append(" id=").append(id).
                append(" uid=").append(uid).
                append(" phoneNumber=").append(phoneNumber).
                append(" contact=").append(contact==null ? "null" : contact.toString()).toString();
    }
}
