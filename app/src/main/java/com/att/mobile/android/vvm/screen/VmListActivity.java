package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.screen.inbox.InboxFragment;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * Created by hginsburg on 3/28/2016.
 */
public class VmListActivity extends VVMActivity implements InboxFragment.ActionListener, InboxFragment.ListListener  {

    private static final String TAG = "VmListActivity";

    protected ModelManager modelManager;

    protected static final int PREFERENCE_REQUEST_CODE = 10;

    protected boolean isCreate = false;

    protected TelephonyManager telephonyManager = null;

    public class RunDeleteByPhonesAsyncTask extends AsyncTask<Void, Void, Void> {
        String[] mPhoneNumbers;

        RunDeleteByPhonesAsyncTask( String[] phoneNumbers) {
            mPhoneNumbers = Arrays.copyOf(phoneNumbers, phoneNumbers.length) ;
        }

        @Override
        protected Void doInBackground(Void... params) {
            int numberOfUpdatedMessage = ModelManager.getInstance().setMessagesAsDeleted(mPhoneNumbers,isSavedTab());
            if ( numberOfUpdatedMessage > 0 ) {
                Long[] messagesUIDs = ModelManager.getInstance().getMessageUIDsToDelete();
                OperationsQueue.getInstance().enqueueDeleteOperation(VmListActivity.this, messagesUIDs);
            }
            return null;
        }

    }

    protected class MarkMessageAsByPhoneNumbersAsyncTask extends AsyncTask<Void, Void, Integer> {

        String[] mPhoneNumbers;
        int mSavedState;

        public MarkMessageAsByPhoneNumbersAsyncTask(String[] phoneNumbers, int savedState) {
            mPhoneNumbers = Arrays.copyOf(phoneNumbers, phoneNumbers.length);
            mSavedState = savedState;
        }

        @Override
        protected Integer doInBackground(Void... params) {

            if ( mPhoneNumbers != null && mPhoneNumbers.length > 0 ) {
                Logger.d(TAG, "MarkMessageAsSaveAsyncTask.doInBackground() start mPhoneNumbers=" + mPhoneNumbers.length);
                return ModelManager.getInstance().setMessagesSavedState(mPhoneNumbers, mSavedState);
            }
            return null;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isCreate = true;

        modelManager = ModelManager.getInstance();

        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isCreate) {
            refreshUi();
        }

        isCreate = false;

        // registers the screen to get model notifications
        modelManager.addEventListener(this);
    }


    @Override
    protected void onPause() {
        Logger.d(TAG, "onPause");

        // unregisters the screen from getting model notifications
        modelManager.removeEventListener(this);

        super.onPause();
    }

    public void onToolbarPlayAllClick ( View buttonView ) {

        Logger.d(TAG, "onToolbarPlayAllClick");
        // creates an intent to launch the message details screen
        Intent intent = new Intent(this, PlayerActivity.class);

        // puts extra data on the intent
        //intent.putExtra(PlayerActivity.IntentExtraNames.EXTRA_ID, messageRowID);
        intent.putExtra(PlayerActivity.IntentExtraNames.FILTER_TYPE, getCurrentFilterType());
        intent.putExtra(PlayerActivity.IntentExtraNames.LAUNCHED_FROM_INBOX, true);
        intent.putExtra(PlayerActivity.IntentExtraNames.IS_AUTO_PLAY_MODE, true);
        intent.putExtra(PlayerActivity.IntentExtraNames.PHONE_NUMBER, getPhoneNumber());

        // launches the message details screen
        startActivityForResult(intent, 0);
    }

    public int getCurrentFilterType () {
        return 0;
    }

    public String getPhoneNumber () {
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.inbox_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_settings: {
                Intent intent = new Intent(getApplicationContext(), PreferencesActivity.class);
                startActivityForResult(intent, PREFERENCE_REQUEST_CODE);
                return true;
            }
            case R.id.menu_refresh: {
                refreshInbox();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * start the retrieve messages operation
     */
    protected void refreshInbox() {

        Logger.d(TAG, "InboxActivity.refreshInbox()");
       /*if (!SimManager.getInstance(this).validateSim().isSimPresentAndReady())
        {
            Logger.d(TAG, "InboxActivity.refreshInbox(): no sim card / sim not ready. Cannot refresh inbox.");
            AlertDlgUtils.showDialog(this, 0, R.string.no_sim_dialog_body_text,
                    R.string.no_sim_dialog_positive_button_text, 0, false, null);
            return;
        }*/
        if (!Utils.isNetworkAvailable()) {
            Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
        }
        OperationsQueue.getInstance().enqueueFetchHeadersAndBodiesOperation();

    }

    protected void initInboxActionBar(String headerStr, boolean shouldDisplayBackOption, boolean playAllEnabled,FontUtils.FontNames fontNames) {

        Logger.d(TAG, "initActionBar shouldDisplayBackOption=" + shouldDisplayBackOption);
        toolbar = (Toolbar) findViewById(R.id.inbox_toolbar);
        toolBarTV = (TextView) findViewById(R.id.header_title);
        toolBarPlayImage = (ImageView) findViewById(R.id.play_btn);
        if (toolbar != null && toolBarTV != null) {

            toolBarTV.setTypeface(FontUtils.getTypeface(fontNames));
            toolBarTV.setText(headerStr);
            setSupportActionBar(toolbar);
            ActionBar supportActionBar = getSupportActionBar();
            supportActionBar.setDisplayShowTitleEnabled(false);

            supportActionBar.setHomeButtonEnabled(shouldDisplayBackOption);
            supportActionBar.setDisplayHomeAsUpEnabled(shouldDisplayBackOption);

        }

        if ( toolBarPlayImage != null ) {
            toolBarPlayImage.setContentDescription(getString(playAllEnabled ? R.string.playAllTxt : R.string.playAllTxtDisabled));
        }

    }

    protected void setToolBarPlayAllBtn ( boolean enabled ) {

        if (telephonyManager != null && telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
            enabled = false;
        }
          if(enabled){
              toolBarPlayImage.setImageResource(R.drawable.play_all);
              toolBarPlayImage.setContentDescription(getString(R.string.playAllTxt));
              toolBarPlayImage.setClickable(true);
        }
        else{
              toolBarPlayImage.setImageResource(R.drawable.play_all_disabled);
              toolBarPlayImage.setContentDescription(getString(R.string.playAllTxtDisabled));
              toolBarPlayImage.setClickable(false);
        }

    }

    protected void showDeleteDialog(final Long[] ids) {
        int titleID;
        int bodyId;
        if (ids != null && ids.length > 1){
            titleID = R.string.deleteMessagesDialogTitle;
            bodyId = R.string.deleteDialogMessages;
        }else{
            titleID = R.string.deleteMessageDialogTitle;
            bodyId = R.string.deleteDialogMessage;
        }

        AlertDlgUtils.showRightAlignedDialog(this, titleID, bodyId, R.string.deleteCAPS, R.string.cancelCAPS, new AlertDlgUtils.AlertDlgInterface() {
            @Override
            public void handlePositiveButton(View view) {
                Logger.i(TAG, "onDeleteAction");
                if (ids != null && ids.length > 0) {
                    deleteVMs(ids);
                    closeEditMode();
                }
            }

            @Override
            public void handleNegativeButton(View view) {
            }
        });
    }

    protected void showDeleteDialog(final String[] phoneNumbers) {

        int titleID;
        int bodyId;
//        if (phoneNumbers != null && phoneNumbers.length > 1){
            titleID = R.string.deleteMessagesDialogTitle;
            bodyId = R.string.deleteDialogMessages;
//        }
//        else{
//            titleID = R.string.deleteMessageDialogTitle;
//            bodyId = R.string.deleteDialogMessage;
//        }
        AlertDlgUtils.showRightAlignedDialog(this, titleID, bodyId, R.string.deleteCAPS, R.string.cancelCAPS, new AlertDlgUtils.AlertDlgInterface() {
            @Override
            public void handlePositiveButton(View view) {
                Logger.i(TAG, "onDeleteAction");
                if (phoneNumbers != null && phoneNumbers.length > 0) {
                    Logger.d(TAG, "deleteVMs()");
                    (new RunDeleteByPhonesAsyncTask(phoneNumbers)).execute();
                    closeEditMode();
                }
            }

            @Override
            public void handleNegativeButton(View view) {
            }
        });
    }

    private boolean isSavedTab() {
        return getCurrentFilterType() == Constants.MessageFilter.TYPE_SAVED;
    }


    @Override
    public void onDeleteAction(Long[] ids) {
        showDeleteDialog(ids);
    }

    @Override
    public void onSaveAction(Long[] ids) {
        markMessagesAs(ids, Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
    }

    @Override
    public void onUnsaveAction(Long[] ids) {
        markMessagesAs(ids, Message.SavedStates.INTERNAL_STORAGE);
    }

    @Override
    public void onDeleteAction(String[] phoneNumbers) {
        showDeleteDialog(phoneNumbers);
    }

    @Override
    public void onSaveAction(String[] phoneNumbers) {
        (new MarkMessageAsByPhoneNumbersAsyncTask(phoneNumbers, Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)).execute();
    }

    @Override
    public void onUnsaveAction(String[] phoneNumbers) {
        (new MarkMessageAsByPhoneNumbersAsyncTask(phoneNumbers, Message.SavedStates.INTERNAL_STORAGE)).execute();
    }

    @Override
    public void onUpdateListener(int eventId, final ArrayList<Long> messageIDs) {

        if (eventId == Constants.EVENTS.MESSAGE_MARKED_AS_SAVED) {
            Logger.d(TAG, "onUpdateListener() MARK_AS_DELETED_FINISHED/MESSAGE_MARKED_AS_SAVED ");
           //in MESSAGE_MARKED_AS_SAVED event the fisrt item in the messageIDs
           // array is the number of the saved VMs
            final boolean isMulipleMessages =messageIDs.get(0) > 1 ;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshUi();
                    showSavedDialog(VmListActivity.this,isMulipleMessages );
                }
            });
            return;
        }
        if (eventId == Constants.EVENTS.MARK_AS_DELETED_FINISHED) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Utils.showToast(R.string.messages_deleted, Toast.LENGTH_LONG);
                }
            });
        }

        super.onUpdateListener(eventId, messageIDs);
    }

    protected void refreshUi()  {

    }

    protected void closeEditMode() {
        // implement in InboxActivity and AggregatedActivity
    }

    @Override
    public void onListUpdated(int type, int size) {

    }
}
