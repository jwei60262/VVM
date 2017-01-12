package com.att.mobile.android.vvm.model.inbox;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.att.mobile.android.infra.utils.AccessibilityUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PicassoUtils;
import com.att.mobile.android.infra.utils.ContactUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.ContactObject;
import com.att.mobile.android.vvm.model.db.inbox.AggregatedVoicemailItem;
import com.att.mobile.android.vvm.model.db.inbox.VoicemailItemBase;
import com.att.mobile.android.vvm.screen.AggregatedActivity;
import com.att.mobile.android.vvm.screen.PlayerActivity;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import java.lang.ref.WeakReference;
import java.util.HashMap;

/**
 * Created by drosenfeld on 01/03/2016.
 */
 
public abstract class ListItemCursorRecyclerAdapterBase extends CursorRecyclerAdapter<ListItemCursorRecyclerAdapterBase.ViewHolder> {

    /*
     * holds an async data loader for lazy loading of resolved contact name and
    * contact photo
    */
    protected Context mContext;
    protected int mFilterType;



    private static final String TAG = ListItemCursorRecyclerAdapterBase.class.getSimpleName();

    protected static HashMap<String, String[]> mContactNamePhotoCashe = new HashMap<String, String[]>();
    protected static final int CONTACT_CASHE_DISPLAY_NAME   = 0;
    protected static final int CONTACT_CASHE_IMAGE_URL      = 1;
    protected static final int CONTACT_CASHE_DEF_RES_ID     = 2;

    protected static String contactInfoContentDescription;
    protected static String addToContactContentDescription;
    protected static String contactDesabledContentDescription;
    protected static String welcomeMessageContentDescription;

    protected ActionModeListener mActionModeListener;
    protected boolean mIsActionModeActive;

    public static void clearContactNamesAndPhotoCashe () {
        Logger.i(TAG, "clearContactNamesAndPhotoCashe");
        mContactNamePhotoCashe.clear();
    }

    public interface ActionModeListener {
        void onItemLongClick(VoicemailItemBase item);
        void onEmptySelection();
    }

    public void setActionModeListener(ActionModeListener actionModeListener) {
        mActionModeListener = actionModeListener;
    }

    public Long[] getSelectedIdsItems() {
        return null;
    }

    public String[] getSelectedPhonesItems () {
        return null;
    }

    public boolean selectByIds () {
        return true;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        //Elements
        public View parent;
        public ImageView photo;
        public ImageView selectedImage;
        public TextView name;
        public ImageView messageStatusView;
        public TextView date;
        public ImageView urgentStatus;
        public ViewFlipper savedOrDownloadFlipper;
        public TextView transcriptionText;

        public ImageView downloadFileGauge;


        public ViewHolder(View itemLayoutView) {
            super(itemLayoutView);

            parent = itemLayoutView;
            photo = (ImageView) itemLayoutView.findViewById(R.id.avatarImage);
            selectedImage = (ImageView) itemLayoutView.findViewById(R.id.selectedImage);
            name = (TextView) itemLayoutView.findViewById(R.id.name);
            messageStatusView = (ImageView) itemLayoutView.findViewById(R.id.messageStatus);
            date = (TextView) itemLayoutView.findViewById(R.id.date);
            urgentStatus = (ImageView) itemLayoutView.findViewById(R.id.urgentStatus);
            savedOrDownloadFlipper = (ViewFlipper) itemLayoutView.findViewById(R.id.savedOrDownloadFlipper);
            transcriptionText = (TextView) itemLayoutView.findViewById(R.id.TranscriptionText);
        }

    }

    public ListItemCursorRecyclerAdapterBase( Context context, int filterType, Cursor c) {
        super(c);
        mContext = context;
        mFilterType = filterType;

        contactInfoContentDescription       = mContext.getString(R.string.contactInfoTxt);
        addToContactContentDescription      = mContext.getString(R.string.addToContactsTxt);
        contactDesabledContentDescription   = mContext.getString(R.string.contactDisabledTxt);
        welcomeMessageContentDescription    = mContext.getString(R.string.welcomeMessagePhoneNumber);
    }

    @Override
    public ListItemCursorRecyclerAdapterBase.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_item, parent, false);

        ListItemCursorRecyclerAdapterBase.ViewHolder vh = new ViewHolder(v);

        return vh;
    }

    protected abstract boolean isSelectedItem ( VoicemailItemBase item );

    protected abstract void toggleSelection ( VoicemailItemBase item );

    protected abstract int getSelectedCount ();

    public void turnEditModeOff () {
        mIsActionModeActive = false;
        notifyDataSetChanged();
    }

    protected class ItemLongClickListener implements View.OnLongClickListener {

        VoicemailItemBase mItem;

        ItemLongClickListener ( VoicemailItemBase item ) {
            mItem = item;
        }

        @Override
        public boolean onLongClick(View v) {

            if(mIsActionModeActive){
                return true;
            }

            mIsActionModeActive = true;

            if(mActionModeListener != null){

                toggleSelection(mItem);

                mActionModeListener.onItemLongClick(mItem);

                AccessibilityUtils.sendEvent(mContext.getString(R.string.selected), v);

                notifyDataSetChanged();
            }

            return true;
        }
    }

    protected class ItemClickListener implements View.OnClickListener {

        private VoicemailItemBase mItem;
        private int itemPosition;

        ItemClickListener ( VoicemailItemBase voicemailItem, int position ) {
            mItem = voicemailItem;
            itemPosition = position;
        }

        @Override
        public void onClick(View v) {
            Logger.i(TAG, "onClick() displayName=" + mItem.getFormattedDisplayName() + " phoneNumber=" + mItem.getPhoneNumber());

            if ( mIsActionModeActive ){

                toggleSelection(mItem);

                if (isSelectedItem(mItem)) {
                    AccessibilityUtils.sendEvent(mContext.getString(R.string.selected), v);
                }else{
                    AccessibilityUtils.sendEvent(mContext.getString(R.string.unselected), v);

                    if ( getSelectedCount() <= 0 ) {
                        mActionModeListener.onEmptySelection();
                    }
                }

            }  else {

                if (mItem instanceof AggregatedVoicemailItem) {
                    AggregatedVoicemailItem aggregatedData = (AggregatedVoicemailItem) mItem;
                    if ((aggregatedData.getNewInboxMessagesCount() + aggregatedData.getOldInboxMessagesCount()) > 1) {
                        openAggregatedScreen();
                        return;
                    }
                }

                openPlayerScreen();

            }
        }

        private void openAggregatedScreen() {

            Logger.i(TAG, "openAggregatedScreen() displayName=" + mItem.getFormattedDisplayName() + " phoneNumber=" + mItem.getPhoneNumber());
            Intent intent = new Intent(mContext, AggregatedActivity.class);
            if ( !TextUtils.isEmpty(mItem.getFormattedDisplayName()) ) {
                intent.putExtra(Constants.INTENT_DATA_USER_NAME, mItem.getFormattedDisplayName());
            }
            intent.putExtra(Constants.INTENT_DATA_USER_PHONE, mItem.getPhoneNumber());
            intent.putExtra(Constants.INTENT_DATA_FILTER_TYPE, mFilterType );

            ContactObject contact = mItem.getContact();
            if(contact != null){
                Logger.i(TAG, "openAggregatedScreen() contact=" + contact.toString());
                Uri contactPhoto = contact.getPhotoUri();
                if(contactPhoto != null){
                    intent.putExtra(Constants.INTENT_DATA_USER_PHOTO_URI, contactPhoto);
                }
            }

            mContext.startActivity(intent);
        }

        private void openPlayerScreen() {

            Intent intent = new Intent(mContext, PlayerActivity.class);

            ContactObject contact = mItem.getContact();
            if( contact != null && contact.getPhotoUri() != null ){
                intent.putExtra(PlayerActivity.IntentExtraNames.CONTACT_URI, contact.getPhotoUri().toString());
                intent.putExtra(PlayerActivity.IntentExtraNames.SENDER_DISPLAY_NAME, contact.getDisplayName());
            }

            intent.putExtra(PlayerActivity.IntentExtraNames.EXTRA_ID, mItem.getId());
            intent.putExtra(PlayerActivity.IntentExtraNames.FILTER_TYPE, mFilterType);
            intent.putExtra(PlayerActivity.IntentExtraNames.LAUNCHED_FROM_INBOX, true);
            intent.putExtra(PlayerActivity.IntentExtraNames.MESSAGE_POSITION, itemPosition);

            mContext.startActivity(intent);

        }
    }

    protected void setEditModeItems(ViewHolder holder, VoicemailItemBase voicemailItem) {

        if (mIsActionModeActive)
        {
            holder.selectedImage.setVisibility(View.VISIBLE);
            holder.photo.setVisibility(View.GONE);
            if ( isSelectedItem(voicemailItem) ) {
                holder.selectedImage.setBackgroundResource(R.drawable.gray_circle);
                holder.selectedImage.setImageResource(R.drawable.ic_action_done);
                holder.selectedImage.setContentDescription(mContext.getText(R.string.selected));
                holder.parent.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray_light3));
            } else {
                holder.selectedImage.setContentDescription(null);
                holder.selectedImage.setBackgroundResource(R.drawable.gray_circle_hollow);
                holder.selectedImage.setImageDrawable(null);
                holder.parent.setBackgroundColor(Color.WHITE);
            }
        }
        else {
            if (isSelectedItem(voicemailItem)) {
                holder.photo.setVisibility(View.GONE);
                holder.selectedImage.setVisibility(View.VISIBLE);
                holder.parent.setBackgroundColor(ContextCompat.getColor(mContext, R.color.gray_light3));
            } else {
                holder.photo.setVisibility(View.VISIBLE);
                holder.selectedImage.setVisibility(View.GONE);
                holder.parent.setBackgroundColor(Color.WHITE);
            }
        }
    }

    protected boolean setDefaultMessage(ViewHolder holder, VoicemailItemBase voicemailItem) {
        if ( voicemailItem.getUid() == Constants.WELCOME_MESSAGE_ID ) {
            updateDisplayNameFromVoicemailItem(holder, voicemailItem);
            if ( holder.photo != null ) {
                Picasso.with(holder.photo.getContext()).load(R.drawable.ic_welcome_avatar).transform(new PicassoUtils.CircleTransform()).noFade().into(holder.photo);
            }
            return true;
        }
        return false;
    }

     /**
     * Sets the default avatar for message's contact, in order related to its position in list.
     * calculation is done respectively to the list size, so new items don't cause the default avatars to change.
      * @return Default avatar resource ID
     */
	 protected static void setDefaultAvatarImage(ViewHolder holder, VoicemailItemBase voicemailItem, boolean saveToChache) {

         int avatarIndex = Constants.AVATAR_IND.DEFAULT.ordinal();
         String phoneNumber = voicemailItem.getPhoneNumber();
         if ( !TextUtils.isEmpty(phoneNumber) ) {
             int ind = phoneNumber.length() - 1;
             if ( ind >= 0 ) {
                 char lastChar = phoneNumber.charAt(phoneNumber.length() - 1);
                 if ( Character.isDigit(lastChar) ) {
                     int lastCharInt = Character.getNumericValue(lastChar);
                     avatarIndex = Constants.AVATAR_FOR_LAST_NUMBER_ARR[lastCharInt];
                 }
             }
         }

         Logger.i(TAG, "setDefaultAvatarImage for phoneNumber=" + phoneNumber + " avatarIndex=" + avatarIndex + " saveToChache=" + saveToChache);
         holder.photo.setImageResource(Constants.DEFAULT_AVATAR_IDs[avatarIndex]);

         if ( saveToChache ) {
             mContactNamePhotoCashe.put(phoneNumber, new String[]{phoneNumber, "", String.valueOf(Constants.DEFAULT_AVATAR_IDs[avatarIndex])});
         }
    }


    protected boolean updatePhotoAndDisplayName(final ViewHolder holder, final VoicemailItemBase voicemailItem, int position) {

        Logger.i(TAG, "updatePhotoAndDisplayName position=" + position + " voicemailItem=" + voicemailItem.toString());
        holder.name.setTag(position);

        String[] contactNamePhotoArr = mContactNamePhotoCashe.get(voicemailItem.getPhoneNumber());
        if ( contactNamePhotoArr != null ) {
            String displayName  = contactNamePhotoArr[CONTACT_CASHE_DISPLAY_NAME];
            if ( !TextUtils.isEmpty(displayName) ) {
                holder.name.setText(displayName);
            }
            final String photoUriStr    = contactNamePhotoArr[CONTACT_CASHE_IMAGE_URL];
            String defAvatarStr         = contactNamePhotoArr[CONTACT_CASHE_DEF_RES_ID];
            int defAvatarId             = Integer.parseInt(defAvatarStr);

            Logger.i(TAG, "updatePhotoAndDisplayName displayName=" + displayName + " photoUriStr=" + photoUriStr + " defAvatarId=" + defAvatarId);
            Uri photoUri = null;
            if ( holder.photo != null && !TextUtils.isEmpty(photoUriStr) ) {
                Picasso.with(holder.photo.getContext()).load(photoUriStr).transform(new PicassoUtils.CircleTransform()).into(holder.photo, new Callback() {
                    @Override
                    public void onSuccess() {
                        Logger.i(TAG, "Picasso onSuccess photoUriStr=" + photoUriStr);
                    }

                    @Override
                    public void onError() {
                        Logger.i(TAG, "Picasso onError photoUriStr=" + photoUriStr);
                        setDefaultAvatarImage(holder, voicemailItem, false);
                    }
                });
                photoUri = Uri.parse(photoUriStr);
            } else if ( holder.photo != null && defAvatarId > 0 ) {
                holder.photo.setImageResource(defAvatarId);
            } else {
                setDefaultAvatarImage(holder, voicemailItem, false);
            }
            // update contact in holder
            voicemailItem.setContact(new ContactObject(photoUri, displayName));
            return true;
        }

        //Update with what we have:
        updateDisplayNameFromVoicemailItem(holder, voicemailItem);

        new ContactLoaderAsync(voicemailItem, holder, position).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        return false;
    }

    private static void updateDisplayNameFromVoicemailItem(ViewHolder holder, VoicemailItemBase voicemailItem) {
        String displayName = voicemailItem.getFormattedDisplayName();
        Logger.i(TAG, "updateDisplayNameFromVoicemailItem displayName=" + displayName);
        if (!TextUtils.isEmpty(displayName) && (holder != null)) {
            holder.name.setText(displayName);
        }
    }

    protected void updateUIAttributesAccordingToReadState(ViewHolder holder, boolean read) {

        Logger.i(TAG, "updateUIAttributesAccordingToReadState read=" + read );
        int textColor = getTextColorForReadState(read);
        holder.messageStatusView.setVisibility(read ? View.GONE : View.VISIBLE);

        Typeface titleTypeFace = getTitleTypeFaceForReadState(read);
        Typeface subTitleTypeFace = getSubTitleTypeFaceForReadState(read);

        if ( holder.name != null ) {
            holder.name.setTextColor(textColor);
            holder.name.setTypeface(titleTypeFace);
        }

        if ( holder.transcriptionText != null ) {
            holder.transcriptionText.setTextColor(textColor);
            holder.transcriptionText.setTypeface(subTitleTypeFace);
        }

        if ( holder.date != null ) {
            holder.date.setTextColor(textColor);
            holder.date.setTypeface(subTitleTypeFace);
        }
    }

    protected Typeface getSubTitleTypeFaceForReadState(boolean read) {
        return read ? FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light) : FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular);
    }

    protected Typeface getTitleTypeFaceForReadState(boolean read) {
        return read ? FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular) : FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium);
    }

    protected int getTextColorForReadState(boolean read) {
        return ContextCompat.getColor(mContext, ( read ? R.color.gray : R.color.gray_dark ) );
    }

    protected static class ContactLoaderAsync extends AsyncTask<Void, Void, ContactObject> {

        private VoicemailItemBase mVoiceMailObject;
        private WeakReference<ListItemCursorRecyclerAdapterBase.ViewHolder> imageViewWeakReference;
        private int position;

        public ContactLoaderAsync(VoicemailItemBase voiceMailObject, ListItemCursorRecyclerAdapterBase.ViewHolder holder, int pos ) {
            mVoiceMailObject = voiceMailObject;
            imageViewWeakReference = new WeakReference<ListItemCursorRecyclerAdapterBase.ViewHolder>(holder);
            position = pos;
        }

        @Override
        protected ContactObject doInBackground(Void... params) {

            String phoneNumber = mVoiceMailObject.getPhoneNumber();
            ContactObject contact = ContactUtils.getContact(phoneNumber);
            Logger.i("ContactLoaderAsync", "doInBackground phoneNumber=" + phoneNumber + " contact=" + (contact==null ? "null" : contact.toString()));

            return contact;
        }

        @Override
        protected void onPostExecute(final ContactObject contactObject) {

            super.onPostExecute(contactObject);

            final ListItemCursorRecyclerAdapterBase.ViewHolder holder = imageViewWeakReference.get();

            if (contactObject != null) {

                Logger.i("ContactLoaderAsync", "onPostExecute contactObject=" + contactObject.toString());
                final Uri photoUri = contactObject.getPhotoUri();
                mContactNamePhotoCashe.put(mVoiceMailObject.getPhoneNumber(), new String[]{contactObject.getDisplayName(), photoUri == null ? "" : contactObject.getPhotoUri().toString(), "0"});

                Integer positionTag = (Integer)holder.name.getTag();
                if ( positionTag != position ) {
                    // Object was reused. Don't set values
                    Logger.i("ContactLoaderAsync", "Object was reused. Don't set values.");
                    return;
                }

                mVoiceMailObject.setContact(contactObject);

                Logger.i("ContactLoaderAsync", "holder=" + holder);
                if (holder != null) {
                    if (holder.photo != null && photoUri != null) {
                        Logger.i("ContactLoaderAsync", "load photoUri=" + photoUri.toString());

                        Picasso.with(holder.photo.getContext()).load(photoUri).transform(new PicassoUtils.CircleTransform()).into(holder.photo, new Callback() {
                            @Override
                            public void onSuccess() {
                                Logger.i(TAG, "Picasso onSuccess photoUri=" + photoUri );
                            }

                            @Override
                            public void onError() {
                                Logger.i(TAG, "Picasso onError photoUri=" + photoUri );
                                setDefaultAvatarImage(holder, mVoiceMailObject, true);
                            }
                        });
                    }
                    updateDisplayNameFromVoicemailItem(holder, mVoiceMailObject);
                }

            }
        }
    }


}


