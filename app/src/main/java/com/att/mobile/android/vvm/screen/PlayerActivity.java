
package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.control.receivers.ContactsContentObserver;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.db.VmContentProvider;

import java.util.ArrayList;

/**
 * The Message Player Activity. Assumptions: 01. the default is start playing 02. voice coming from ear phone and not
 * via speaker
 */
public class PlayerActivity extends VVMActivity  implements LoaderManager.LoaderCallbacks<Cursor>, AudioManager.OnAudioFocusChangeListener{

	private static final String TAG = "PlayerActivity";

	private ViewPager viewPager = null;
        // holds IDs of the new message which were reviewed in this screen
    // and needs to be marked as read when the screen is paused
    // <key:messageID, value:messageUID>
//    private static HashMap<Long, Long> messagesToMarkAsRead = null;
    private String contactUri = null;
    private String displayName = null;


    /** holds whether the player is in auto-play mode */
    private boolean isAutoPlayMode = false;
    private boolean goToInboxCalled = false;

    private static final int URL_LOADER = 0;
    private Message message;
    private int mespos;
    private Cursor mesCursor;
    private int filterType;
    private ModelManager mModelManager;
    private String mCurrentPhoneNumber;
    private long messageId;
    private ContactsContentObserver contactsObserver;

    public CursorPagerAdapter getCursoradapter() {
        return cursoradapter;
    }
    // holds whether the screen was launched by the inbox screen
    private boolean screenLaunchedByInbox = false;

    private CursorPagerAdapter cursoradapter;

    @Override
    public void onAudioFocusChange(int focusChange) {

    }

    private class RunUpdateReadAsyncTask extends AsyncTask<Void, Void, Void> {
        long messageID;


        RunUpdateReadAsyncTask( long messageID ) {
            this.messageID = messageID;
        }

        @Override
        protected Void doInBackground(Void... params) {
            ModelManager.getInstance().setMessageAsRead(messageID);
            OperationsQueue.getInstance().enqueueMarkAsReadOperation(PlayerActivity.this, messageID);
            return null;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {

        boolean isGroupAndAggreg = mModelManager.isGroupByContact() && (mCurrentPhoneNumber!=null);
        String where =  isGroupAndAggreg ? (filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_PHONE_NUMBER_AND_SAVED : ModelManager.WHERE_PHONE_NUMBER_AND_SAVED_NOT) :
                (filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_SAVED : ModelManager.WHERE_SAVED_NOT);
        String[] selectArgs = isGroupAndAggreg ? new String[]{ String.valueOf(mCurrentPhoneNumber), String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)}
                : new String[] {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};

        Logger.i(TAG, "onCreateLoader filterType=" + filterType + " where=" + where);

        return new CursorLoader(
                this,                                           // Parent activity context
                VmContentProvider.CONTENT_URI,                  // Table to query
                ModelManager.COLUMNS_VM_LIST,                   // Projection to return
                where,                                          // No selection clause
                selectArgs,                                     // No selection arguments
                ModelManager.Inbox.KEY_TIME_STAMP + " desc"     // Default sort order
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Logger.i(TAG, "onLoadFinished");
        refreshCursorData(data);
    }

    private void refreshCursorData(Cursor data) {
        if(mespos < 0 && !screenLaunchedByInbox){
            filterType = mModelManager.isMessageSaved(messageId) ? Constants.MessageFilter.TYPE_SAVED : Constants.MessageFilter.TYPE_ALL;
            data = getMesCursor(filterType, mCurrentPhoneNumber);
            mespos = mModelManager.getMessagePosition(messageId,filterType,mCurrentPhoneNumber) - 1;
            data.moveToPosition(mespos);
        }
        if(cursoradapter == null) {
            FragmentManager fm = getSupportFragmentManager();
            cursoradapter = new CursorPagerAdapter(fm, data, isAutoPlayMode);
            viewPager.setAdapter(cursoradapter);
            data.moveToFirst();
        } else {
            cursoradapter.changeCursor(data);

        }
        message = mModelManager.createMessageFromCursor(data);
        if(message != null &&!TextUtils.isEmpty(displayName)){
            message.setSenderDisplayName(displayName);
        }
        if(message != null &&!TextUtils.isEmpty(contactUri)){
            message.setContactImageUri(Uri.parse(contactUri));
        }
        viewPager.setCurrentItem(mespos);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if ( cursoradapter != null ) {
            cursoradapter.changeCursor(null);
        }
    }
    // holds the filter type to handle messages according to

    /**
     * Holds the names for the possible extra data in the intent launching this activity.
     */
    public static interface IntentExtraNames {
        public static final String EXTRA_ID = "id";
        public static final String FILTER_TYPE = "filterType";
        public static final String LAUNCHED_FROM_INBOX = "launchedFromInbox";
        public static final String IS_AUTO_PLAY_MODE = "isAutoPlayMode";
        public static final String MESSAGE_POSITION = "messagePosition";
        public static final String SENDER_DISPLAY_NAME = "senderDisplayName";
        public static final String CONTACT_URI = "contactUri";
        public static final String CURRENT_MESSAGE = "currentMessage";
        public static final String PHONE_NUMBER = "phoneNumber";
    }
    private SensorManager mSensorManager;
    private Sensor mProximity;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private int field = 0x00000020;
    private boolean isProximityHeld = false;
    private boolean wasSpeakerSwaped = false;
    private AudioManager audioManager;

    private SensorEventListener mSensorEventListener = new SensorEventListener(){

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] == 0) {
                if(!isProximityHeld) {
                    Logger.d(TAG, "onSensorChanged event.values[0] == 0");
                    isProximityHeld = true;
                    if(vvmApplication.isApplicationSpeakerOn()){
                        if(!vvmApplication.isCurrentlyApplicationAudioMode()){
                            vvmApplication.setCurrentlyApplicationAudioMode(true);
                        }
                        swapAudioSource();
//                        if(!isAutoPlayMode){
                            wasSpeakerSwaped = true;
//                        }
                    }
                    wakeLock.acquire();
                }
            } else {
                if(isProximityHeld) {
                    Logger.d(TAG, "onSensorChanged event.values[0] != 0");
                    wakeLock.release();
                    isProximityHeld = false;
                    if(wasSpeakerSwaped && !vvmApplication.isApplicationSpeakerOn()){
                        wasSpeakerSwaped = false;
                        if(!vvmApplication.isCurrentlyApplicationAudioMode()){
                            vvmApplication.setCurrentlyApplicationAudioMode(true);
                        }
                        swapAudioSource();
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

    };

    /**
     * Swaps application's audio source (between earphone and speaker).
     */
     void swapAudioSource() {
        Logger.d(TAG, "swapAudioSource()");
		if(!VVMApplication.getInstance().isCurrentlyApplicationAudioMode()){
        VVMApplication.getInstance().setApplicationAudioMode();
		}
        VVMApplication.getInstance().setIsApplicationSpeakerOn(!VVMApplication.getInstance().isApplicationSpeakerOn());
        audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        Logger.d(TAG, "swapAudioSource() ended. audioManager.isSpeakerphoneOn() = " + audioManager.isSpeakerphoneOn());

    }

    /** Called when the activity is first created. */


    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);

        mModelManager = ModelManager.getInstance();
        // gets the intent handled by this activity
        Intent intent = getIntent();

        // stores the ID of the message to be handled by the screen,
        // the current filter type to play message from (when moving to the previous / next message)
        // and whether the screen was launced from the Inbox screen
        // the message id in the db
        messageId = -1;
        // holds whether the screen was launched by the inbox screen
        loadIntentExtras(intent);

        viewPager = (ViewPager) findViewById(R.id.awesomepager);
        //viewPager.setOffscreenPageLimit(10);

        mModelManager.addEventListener(this);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        try {
            field = PowerManager.class.getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
            Logger.d(TAG, "onCreate()  hidden field PROXIMITY_SCREEN_OFF_WAKE_LOCK = "+field);
        } catch (Throwable ignored) {
            Log.e(TAG, "onCreate() Exception while getting hidden field PROXIMITY_SCREEN_OFF_WAKE_LOCK");
        }

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(field, getPackageName());
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        getSupportLoaderManager().initLoader(URL_LOADER, null, this);
    }

    private void loadIntentExtras(Intent intent) {
        Bundle bundle = intent.getExtras();
        if(bundle != null) {
            messageId = bundle.getLong(IntentExtraNames.EXTRA_ID);
            filterType = bundle.getInt(IntentExtraNames.FILTER_TYPE, -1);
            if(filterType < 0 && messageId > 0){
                filterType = mModelManager.isMessageSaved(messageId) ? Constants.MessageFilter.TYPE_SAVED : Constants.MessageFilter.TYPE_ALL;
            }
            contactUri = bundle.getString(IntentExtraNames.CONTACT_URI);
            displayName = bundle.getString(IntentExtraNames.SENDER_DISPLAY_NAME);
            mespos = bundle.getInt(IntentExtraNames.MESSAGE_POSITION, -1);
//            mCurrentPhoneNumber = mModelManager.getSenderPhoneNumber(messageId);
            mCurrentPhoneNumber = bundle.getString(IntentExtraNames.PHONE_NUMBER, null);
            mCurrentPhoneNumber = mCurrentPhoneNumber!=null ? mCurrentPhoneNumber : messageId > 0? mModelManager.getSenderPhoneNumber(messageId) : null;
            isAutoPlayMode = bundle.getBoolean(IntentExtraNames.IS_AUTO_PLAY_MODE, false);
            screenLaunchedByInbox = bundle.getBoolean(IntentExtraNames.LAUNCHED_FROM_INBOX, true);

        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        Logger.d(TAG, "onPostCreate");
        super.onPostCreate(savedInstanceState);
        ContactsContentObserver.createInstance(handler);
        contactsObserver = ContactsContentObserver.getInstance();
        contactsObserver.addEventListener(this, this);
    }

    private Cursor getMesCursor(int filterType, Message currmessage){
    boolean isGroupByContact = mModelManager.isGroupByContact();
    String messageByPhone = isGroupByContact ? mCurrentPhoneNumber : null;
     Cursor mesCursor = mModelManager.getAllMessagesFromInbox(filterType, messageByPhone);
    return mesCursor;
}
    private Cursor getMesCursor(int filterType, String messageByPhone){
        Cursor mesCursor = mModelManager.getAllMessagesFromInbox(filterType, messageByPhone);
        return mesCursor;
    }

    void switchPage(int nPage){
        viewPager.setCurrentItem(nPage);
    }
    void refreshAdapter(long messageId, int pos){
        message = mModelManager.createMessageFromCursor(mModelManager.getMessage(messageId));
        Logger.d(TAG, "refreshAdapter() messageId = "+ messageId + "mesposition = "+ pos);
        mesCursor = getMesCursor(filterType, message);
        if(mesCursor != null && mesCursor.getCount() > 0) {
            Logger.d(TAG, "refreshAdapter  messageId = " + messageId + " message.getRowId() = " + (message != null ? message.getRowId() : "null") + " mesCursor count = " + mesCursor.getCount() + "mespos = " + mespos);
            mespos = mModelManager.getMessagePosition(message.getRowId(), filterType, mCurrentPhoneNumber) - 1;
            mesCursor.moveToPosition(mespos);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshCursorData(mesCursor);
                }
                });
        }
    }

    void registerProximityListener(){
        Logger.d(TAG, "registerProximityListener");
        mSensorManager.registerListener(mSensorEventListener, mProximity, SensorManager.SENSOR_DELAY_FASTEST);
    }

    void unregisterProximityListener(){
        Logger.d(TAG, "unregisterProximityListener");
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    boolean wasProximityHold(){
        return isProximityHeld;
    }

    @Override
    protected void onDestroy() {
        if(mModelManager != null) {
            mModelManager.removeEventListener(this);
        }
        if(contactsObserver != null) {
            contactsObserver.removeEventListener(this, this);
        }
        unregisterProximityListener();
        if(mesCursor != null){
            mesCursor.close();
        }
        cursoradapter = null;

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }
    }

    @Override
    public void onBackPressed() {
        unregisterProximityListener();
        if (!screenLaunchedByInbox) {
            goToInbox();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        loadIntentExtras(intent);
        getSupportLoaderManager().initLoader(URL_LOADER, null, this);
    }

    /**
     * Moves to the inbox screen.
     */
    private void goToInbox() {

        Logger.d(TAG, "goToInbox()");

        // in case the screen was not created by the inbox screen (but by a new
        // message notification)
        if (!screenLaunchedByInbox) {
            // block other threads from entering while the flag is updated
            synchronized (this) {

                if (!goToInboxCalled) {
                    goToInboxCalled = true;
                    // launch the inbox screen only of not launch before
                    Intent intent = new Intent(getApplicationContext(),	InboxActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            }
        }
        finish();

        Logger.d(TAG, "goToInbox() ended");
    }


    public  void updateMessageToMarkAsReadStatus(long uid) {

        Logger.d(TAG, "updateMessageToMarkAsReadStatus()");
        (new RunUpdateReadAsyncTask(uid)).execute();
        vvmApplication.updateNotification();

        Logger.d(TAG, "updateMessageToMarkAsReadStatus() ended");
    }

    @Override
    protected void refreshContacts() {
        Logger.d(TAG, "refreshContacts()");
        if(cursoradapter != null) {
            cursoradapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        if(!wasProximityHold() && mModelManager != null) {
            mModelManager.notifyListeners(Constants.EVENTS.PLAYER_PAUSED, null);
        }
        super.onPause();
    }

    @Override
    public void onUpdateListener(final int eventId, ArrayList<Long> messageIDs) {


           Message currmessage = cursoradapter != null ? cursoradapter.getCurrentMessage(): null;

            if (currmessage != null && ((eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED) || (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_SAVED) || (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_UNSAVED) || (eventId == Constants.EVENTS.DELETE_FINISHED) /*|| (eventId == Constants.EVENTS.RETRIEVE_HEADERS_FINISHED)*/)) {

                int adapterCount = cursoradapter != null ? cursoradapter.getCount() : 0;
                Logger.d(TAG, " onUpdateListener()  currmessageID = "+(currmessage != null ? currmessage.getRowId() : "null") + " messageId = "+ messageId+ " message.getRowId() = "+(message != null ? message.getRowId() : "null")+ " adapterCount = "+adapterCount+ "mespos = "+ mespos);
                mesCursor = getMesCursor(filterType, currmessage);
                if(mesCursor != null && mesCursor.getCount() > 0 || eventId == Constants.EVENTS.MESSAGE_MARKED_AS_SAVED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_SAVED) {
                                showSavedDialog(PlayerActivity.this, false);
                            } else if(eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED){
                                Utils.showToast(R.string.message_deleted, Toast.LENGTH_LONG);

                            }

                            if(mesCursor != null && mesCursor.getCount() > 0 ) {
                                if(cursoradapter != null) {
                                    cursoradapter.changeCursor(mesCursor);
                                }
                                if(viewPager != null) {
                                    viewPager.invalidate();
                                }
                            }
                        }
                    });
                } else {
                    // in case the last message was deleted - show the toast ( defect #8670)
                    if(eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Utils.showToast(R.string.message_deleted, Toast.LENGTH_LONG);
                            }
                        });
                    }
                    finish();
                }
            } else if (currmessage == null && ((eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED) || (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_SAVED) || (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_UNSAVED) || (eventId == Constants.EVENTS.DELETE_FINISHED))) {
                finish();

            } 	else {
                super.onUpdateListener(eventId, messageIDs);
            }
    }

    @Override
    protected void goToGotItScreen() {
        Logger.i(TAG, "goToGotItScreen");
        if ( mesCursor == null || mesCursor.getCount() <= 0 ) {
            finish();
        }
    }

}
