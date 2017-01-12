package com.att.mobile.android.vvm.screen;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.ContactUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PicassoUtils;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.receivers.ContactsContentObserver;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.ContactObject;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.screen.inbox.InboxFragment;
import com.squareup.picasso.Picasso;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Created by hginsburg on 3/13/2016.
 */
public class AggregatedActivity extends VmListActivity {

    private static final String TAG = AggregatedActivity.class.getSimpleName();

    private InboxFragment listFragment;
    private String mCurrentPhoneNumber;
    private String mDisplayName;
    private Uri mCurrentPhotoUri;
    ImageView mUserImage;
    private int filterType;

    private class ContactLoaderAsync extends AsyncTask<Void,Void,ContactObject> {

        WeakReference<ImageView> mUserImageRef;
        WeakReference<TextView> mToolBarTitleRef;
        private String mPhoneNumber;

        public ContactLoaderAsync(String phoneNumber, TextView toolBarTitle, ImageView userImage){

            mPhoneNumber= phoneNumber;
            mUserImageRef = new WeakReference<>(userImage);
            mToolBarTitleRef = new WeakReference<>(toolBarTitle);
        }

        @Override
        protected ContactObject doInBackground(Void... params) {

            return ContactUtils.getContact(mPhoneNumber);
        }

        @Override
        protected void onPostExecute(ContactObject contactObject) {
            super.onPostExecute(contactObject);

            TextView titleBar = mToolBarTitleRef.get();
            final ImageView userImage = mUserImageRef.get();

            if(contactObject != null){

                String displayName = contactObject.getDisplayName();
                Logger.i(TAG, "#### ContactLoaderAsync displayName=" + displayName);

                if(titleBar != null){
                    titleBar.setText( TextUtils.isEmpty(displayName) ? mPhoneNumber : displayName );
                }

                Uri contactPhotoUri = contactObject.getPhotoUri();
                mCurrentPhotoUri = contactPhotoUri;
                if( userImage != null && contactPhotoUri != null ){
                    Picasso.with(userImage.getContext()).load(contactPhotoUri).into(userImage);
                } else {
                    setColorsAndAvatarImage(userImage, mPhoneNumber);

                }
            } else {
                if(titleBar != null){
                    titleBar.setText(mPhoneNumber);
                }
                if(userImage != null){
                    setColorsAndAvatarImage(userImage, mPhoneNumber);
                }
            }
        }
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = AggregatedActivity.this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(AggregatedActivity.this.getResources().getColor(color));
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.aggregated);

        mUserImage = (ImageView) findViewById(R.id.agg_user_image);

        mCurrentPhoneNumber = getIntent().getStringExtra(Constants.INTENT_DATA_USER_PHONE);
        mCurrentPhotoUri = getIntent().getParcelableExtra(Constants.INTENT_DATA_USER_PHOTO_URI);
        String displayName = getIntent().getStringExtra(Constants.INTENT_DATA_USER_NAME);
        filterType = getIntent().getIntExtra(Constants.INTENT_DATA_FILTER_TYPE, Constants.MessageFilter.TYPE_ALL);

        Logger.i(TAG, "onCreate mCurrentPhoneNumber=" + mCurrentPhoneNumber + " mCurrentPhotoUri=" + mCurrentPhotoUri + " displayName=" + displayName + " filterType=" + filterType);

        mDisplayName = TextUtils.isEmpty(displayName) ? mCurrentPhoneNumber : displayName;

        initInboxActionBar(mDisplayName, true, true, FontUtils.FontNames.Roboto_Regular);
        toolBarTV.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.toolbarTextSize));


        listFragment = InboxFragment.newInstance(filterType, mCurrentPhoneNumber, true);
        listFragment.setActionListener(this);
        listFragment.setListListener(this);

        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, listFragment).commit();

        updateUserPicBackground();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        ContactsContentObserver.createInstance(handler);
        ContactsContentObserver.getInstance().addEventListener(this, this);
    }

    protected void updateUserPicBackground () {

        Logger.i(TAG, "updateUserPicBackground");
        if (mCurrentPhotoUri != null) {
            Picasso.with(this).load(mCurrentPhotoUri).into(mUserImage);
        } else {
            setColorsAndAvatarImage(mUserImage, mCurrentPhoneNumber);

        }
    }

    private void setColorsAndAvatarImage(ImageView userImage, String phoneNumber) {
        int color = Utils.getDefaultAvatarBackground(phoneNumber);
        userImage.setBackgroundColor(AggregatedActivity.this.getResources().getColor(color));
        PicassoUtils.loadDefaultImage(userImage, R.drawable.avatar_short);
        setStatusBarColor(color);
        changeToolbarColor(color);
    }

    private void changeToolbarColor(int color) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.inbox_toolbar);
        toolbar.setBackgroundColor(getResources().getColor(color));
    }

    @Override
    protected void onResume() {

        Logger.i(TAG, "onResume isCreate=" + isCreate);
        super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }

        if (!isCreate) {
            updateUserPicBackground();
            refreshUi();
        }
    }

    @Override
    public int getCurrentFilterType() {
        return filterType;
    }

    @Override
    public String getPhoneNumber() {
        return mCurrentPhoneNumber;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Logger.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

        if (requestCode == PREFERENCE_REQUEST_CODE) {
            boolean groupedByContact = ModelManager.getInstance().isGroupByContact();
            Logger.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
            if (!groupedByContact) {
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onUpdateListener(final int eventId, ArrayList<Long> messageIDs) {

        Logger.d(TAG, "onUpdateListener() eventId=" + eventId);
        switch (eventId) {

            case Constants.EVENTS.NEW_MESSAGE:
            case Constants.EVENTS.MARK_AS_DELETED_FINISHED:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED){
                            Utils.showToast(R.string.messages_deleted, Toast.LENGTH_LONG);
                        }
                        refreshUi();
                    }
                });
                break;
            case Constants.EVENTS.MESSAGE_MARKED_AS_UNSAVED:
                refreshUIWithHandler();
                break;

            default:
                super.onUpdateListener(eventId, messageIDs);
        }


    }

    @Override
    protected void refreshContacts() {

        new ContactLoaderAsync(mCurrentPhoneNumber, toolBarTV, mUserImage).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshUIWithHandler() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                refreshUi();
            }
        });
    }

    @Override
    protected void refreshUi( ) {

        Logger.i(TAG, "refreshUi");
        listFragment.refreshList(false);
    }

    @Override
    protected void goToGotItScreen() {

        Logger.i(TAG, "goToGotItScreen");
        if ( listFragment.getItemCount() == 0 ) {
            finish();
        }
    }

    @Override
    public void onListUpdated(int type, int size) {

        if ( size == 0 &&
                ( filterType == Constants.MessageFilter.TYPE_SAVED ||
                        ModelManager.getInstance().getSharedPreferenceValue(Constants.DO_NOT_SHOW_SAVED_DIALOG_AGAIN, Boolean.class, false)) ) {
            finish();
        }
        setToolBarPlayAllBtn( size!=0 );
    }

    @Override
    protected void closeEditMode() {
        listFragment.closeEditMode();
    }

    
}