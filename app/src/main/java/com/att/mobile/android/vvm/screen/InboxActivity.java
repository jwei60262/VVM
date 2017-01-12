package com.att.mobile.android.vvm.screen;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.control.receivers.ContactsContentObserver;
import com.att.mobile.android.vvm.gcm.RegistrationIntentService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.inbox.ListItemCursorRecyclerAdapterBase;
import com.att.mobile.android.vvm.screen.inbox.InboxFragment;
import com.att.mobile.android.vvm.screen.inbox.InboxFragmentPagerAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.PicassoTools;

import java.util.ArrayList;

/**
 * The InboxAcvtivity class in the main Activity on the applications you can see the list of all Item
 * 
 * @author Amit Krelman
 *
 */

public class InboxActivity extends VmListActivity {

	private boolean isErrorDownloadMessgesShown = false;
	/**
	 * holds the position of the last long clicked message
	 */
	private Dialog maxMessagesDialog = null;
	private boolean showMaxMessagesDialog = false;
	private boolean showAlmostMaxMessagesDialog = false;
	/**
	 * used to block handling the SIM_VALID event after first time that the event was handled
	 */
	private boolean simValid = false;

//	/** holds the auto-play button */
//	private LinearLayout autoPlayButton = null;

	private InboxFragmentPagerAdapter mInboxFragmentPagerAdapter;
	private ViewPager mViewPager;
	private TabLayout mTabLayout;

	//Constants
	private static final String TAG = InboxActivity.class.getSimpleName();

	private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 11;

	private static final int DIALOG_MESSAGES_ALLMOST_FULL = 5;
    private static final int DIALOG_MESSAGE_FULL = 6;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Logger.d(TAG, "InboxActivity::onCreate");

		super.onCreate(savedInstanceState);

		setContentView(R.layout.inbox);

		initUIElements();

		// Create the Handler. It will implicitly bind to the Looper
		// that is internally created for this thread (since it is the UI
		// thread)

		if (modelManager == null) {
			modelManager = ModelManager.getInstance();
		}

		// reset flag to be able to show the max messages dialog
		showMaxMessagesDialog = true;
		showAlmostMaxMessagesDialog = true;

		registerGcm();
	}

	private void registerGcm()
	{
		if(isGooglePlayServicesAvailable())
		{
			startService(new Intent(this, RegistrationIntentService.class));
		}
	}

	private boolean isGooglePlayServicesAvailable()
	{
		GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
		int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
		Log.d(TAG, "Google Play Services Availability result: " + apiAvailability.getErrorString(resultCode));
		return resultCode == ConnectionResult.SUCCESS;
	}

	private void initUIElements() {

        boolean showPlayAllBtn = false;
        initInboxActionBar(getString(R.string.visual_voicemail), false, showPlayAllBtn , FontUtils.FontNames.OmnesATT_Light);
		initViewPager();
		initTabLayout();
	}

	private void initViewPager() {

		mViewPager = (ViewPager) findViewById(R.id.view_pager);
		mInboxFragmentPagerAdapter = new InboxFragmentPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mInboxFragmentPagerAdapter);
        mInboxFragmentPagerAdapter.setActionListener(this);
        mInboxFragmentPagerAdapter.setListListener(this);
	}

	private void initTabLayout() {

        Logger.i(TAG, "initTabLayout");
		mTabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
		mTabLayout.setupWithViewPager(mViewPager);
//        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

		// Iterate over all tabs and set the custom view
		for (int i = 0; i < mTabLayout.getTabCount(); i++) {
			TabLayout.Tab tab = mTabLayout.getTabAt(i);
			tab.setContentDescription(mInboxFragmentPagerAdapter.getContentDescription(i));
		}

	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		Logger.d(TAG, "onPostCreate");
		super.onPostCreate(savedInstanceState);

		OperationsQueue.getInstance().addEventListener(this);

		ContactsContentObserver.createInstance(handler);
		ContactsContentObserver.getInstance().addEventListener(this, this);
	}

	@Override
	protected void onResume() {

		Logger.d(TAG, "onResume");
        super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }

        Intent inboxIntent = getIntent();
        if (inboxIntent != null && inboxIntent.getAction() != null) {
            // if we started by the history screen (long press on HOME) we don't
            // apply special launch from notification behavior
            if ( (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
                String action = getIntent().getAction();
                if (action != null && action.equals(ACTIONS.ACTION_LAUNCH_INBOX_FROM_NOTIFICATION)) {

                    Logger.d(TAG, "onResume() action = ACTION_LAUNCH_INBOX_FROM_NOTIFICATION");
					mTabLayout.getTabAt(InboxFragmentPagerAdapter.sAllInboxItemsTabIndex).select();
                    // in order to avoid processing the same intent more than
                    // once
                    setIntent(null);
                }
            }
        }

        if( inboxIntent != null && inboxIntent.getAction()!= null && inboxIntent.getAction().equals(Constants.ACTION_GOTO_SAVED)){
            if(mViewPager != null){
				mTabLayout.getTabAt(InboxFragmentPagerAdapter.sSavedInboxItemsTabIndex).select();

            }
			inboxIntent.setAction(null);
        }
		if(inboxIntent != null && inboxIntent.getBooleanExtra(Constants.EXTRAS.EXTRA_REFRESH_INBOX, false)){
			refreshInbox();
			modelManager.setNeedRefreshInbox(false);
		}
	}


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Logger.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

        if ( requestCode == PermissionsActivity.PERMISSION_ACTIVITY_RESULT ) {
            simManager.startSimValidation();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

		Logger.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
		switch (requestCode) {

			case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
				if (PermissionUtils.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
//					TODO: Inbox Refactoring - action bar:
//					exportToFile();
				} else if (PermissionUtils.wasNeverAskAgainChecked(Manifest.permission.WRITE_EXTERNAL_STORAGE, this)) {
					AlertDlgUtils.showDialog(this, R.string.permissions, R.string.enable_permissions_dialog, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
						@Override
						public void handlePositiveButton(View view) {
						}

						@Override
						public void handleNegativeButton(View view) {
						}
					});
				}			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	/**
	 * Called upon launching an instance of this activity again, with another intent.
	 */
	@Override
	public void onNewIntent(Intent newIntent) {
		// sets the new intent as the launching intent
		setIntent(newIntent);
	}

	private void refreshFragments( boolean groupedByContact ) {
		mInboxFragmentPagerAdapter.refreshList(groupedByContact);
	}

    @Override
    protected void refreshContacts() {
        Logger.d(TAG, "refreshContacts()");
        ListItemCursorRecyclerAdapterBase.clearContactNamesAndPhotoCashe();
        PicassoTools.clearCache(Picasso.with(this));
    }

    @Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {
		switch (eventId) {

			case EVENTS.LOGIN_FAILED_DUE_TO_WRONG_PASSWORD:
				Logger.d(TAG, "onUpdateListener() LOGIN_FAILED_DUE_TO_WRONG_PASSWORD");
				// password change is needed, turn the key ON
				handler.post(new Runnable() {
					@Override
					public void run() {
						setResult(EVENTS.START_WELCOME_ACTIVITY);
//						finish();
					}
				});
				break;

			case EVENTS.SIM_VALID:
				Logger.d(TAG, "onUpdateListener() SIM_VALID");
				if (!simValid) {
					// mark the sim was validate in order to avoid refreshing the
					// list every time the event is sent
					simValid = true;
					if (!modelManager.wasMailboxRefreshedOnStartup() && hasWindowFocus()) {
						Logger.d(TAG, "onUpdateListener() SIM_VALID going to refresh inbox");
						refreshInbox();
					}
				}
				break;

			case EVENTS.RETRIEVE_MESSAGES_STARTED:
				Logger.d(TAG, "onUpdateListener() RETRIEVE_MESSAGES_STARTED");
				// we want to modify the progress bar so we need to do it from the
				// UI thread
				// how can we make sure the code runs in the UI thread? use the
				// handler!
				handler.post(new Runnable() {
					@Override
					public void run() {
//						TODO: Inbox Refactoring - Snack bar:

//						errorLayout.setVisibility(View.GONE);
//						isErrorDownloadMessgesShown = false;
//						startGauge();
					}
				});

				break;

			case EVENTS.RETRIEVE_HEADERS_FINISHED:
				Logger.d(TAG,
						"onUpdateListener() RETRIEVE_HEADERS_FINISHED");
				// we want to modify the progress bar so we need to do it from the
				// UI thread
				// how can we make sure the code runs in the UI thread? use the
				// handler!
				handler.post(new Runnable() {
					@Override
					public void run() {
						stopGauge();
					}
				});
				break;

			case EVENTS.RETRIEVE_BODIES_FINISHED:
				Logger.d(TAG,
						"onUpdateListener() RETRIEVE_BODIES_FINISHED");
				// we want to modify the progress bar so we need to do it from the
				// UI thread
				// how can we make sure the code runs in the UI thread? use the
				// handler!
				handler.post(new Runnable() {
					@Override
					public void run() {
						stopGauge();
					}
				});
				break;

			case EVENTS.SELECT_INBOX_FINISHED_WITH_NO_MESSAGES:
				Logger.d(TAG,
						"onUpdateListener() SELECT_INBOX_FINISHED_WITH_NO_MESSAGES");
				// we want to modify the progress bar so we need to do it from the
				// UI thread
				// how can we make sure the code runs in the UI thread? use the
				// handler!
				handler.post(new Runnable() {
					@Override
					public void run() {
						stopGauge();
					}
				});
				break;

			case EVENTS.RETRIEVE_BODIES_FAILED_NOT_ENOUGH_SPACE:
				Logger.d(TAG,
						"onUpdateListener() RETRIEVE_BODIES_FAILED_NOT_ENOUGH_SPACE");
				break;

			case EVENTS.MESSAGES_ALLMOST_FULL:
				Logger.d(TAG,
						"onUpdateListener() MESSAGES_ALLMOST_FULL");
				if (showAlmostMaxMessagesDialog) {
					showAlmostMaxMessagesDialog = false;
					handler.post(new Runnable() {
						@Override
						public void run() {
							showMaxMessagesDialog(DIALOG_MESSAGES_ALLMOST_FULL);
						}
					});
				}
				break;

			case EVENTS.MESSAGES_FULL:
				Logger.d(TAG, "onUpdateListener() MESSAGES_FULL");
				if (showMaxMessagesDialog) {
					showMaxMessagesDialog = false;
					handler.post(new Runnable() {
						@Override
						public void run() {

							showMaxMessagesDialog(DIALOG_MESSAGE_FULL);
						}
					});
				}
				break;

			case EVENTS.DELETE_FINISHED:
				Logger.d(TAG, "onUpdateListener() DELETE_FINISHED");
				Logger.d(TAG, "InboxActivity.onUpdateListener() notifyListeners EVENTS.DELETE_FINISHED");
				break;

			case EVENTS.CONTACTS_CHANGED:
				Logger.d(TAG, "onUpdateListener() CONTACTS_CHANGED");
                super.onUpdateListener(eventId, messageIDs);
				return;

			case EVENTS.CONNECTION_LOST:
				Logger.d(TAG, "onUpdateListener() CONNECTION_LOST");
				handler.post(new Runnable() {
					@Override
					public void run() {
						Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
						// loading indication is removed
						stopGauge();
					}
				});
				break;

			case EVENTS.FETCH_BODIES_MAX_RETRIES:
				Logger.d(TAG, "onUpdateListener() FETCH_BODIES_MAX_RETRIES");
//				TODO: Inbox Refactoring - Snack bar:
				handler.post(new Runnable() {
					@Override
					public void run() {
						Utils.showToast(R.string.cantDownloadMessagesError, Toast.LENGTH_LONG);

//						isErrorDownloadMessgesShown = true;
						// loading indication is removed
						stopGauge();
					}
				});
				break;

			case EVENTS.CONNECTION_CONNECTED:
				Logger.d(TAG, "onUpdateListener() CONNECTION_CONNECTED");
				//TODO: Inbox Refactoring - Snack bar:
//				handler.post(new Runnable() {
//					@Override
//					public void run() {
//						errorLayout.setVisibility(View.GONE);
//					}
//				});
				break;

			// already handled outside of the switch - just a log print here
			case EVENTS.REFRESH_UI:
				Logger.d(TAG, "onUpdateListener() REFRESH_UI");
				break;

			// already handled outside of the switch - just a log print here
			case EVENTS.NEW_MESSAGE:
				Logger.d(TAG, "onUpdateListener() NEW_MESSAGE");
				break;


			case EVENTS.LOGIN_FAILED:
				Logger.d(TAG, "onUpdateListener() LOGIN_FAILED");
				handler.post(new Runnable() {
					@Override
					public void run() {
						stopGauge();
					}
				});
				break;

			default:
				Logger.d(TAG, "onUpdateListener() Event ID: " + eventId);
				break;
		}

		// will refresh on every event!
		handler.post(new Runnable() {
			@Override
			public void run() {
				refreshUi();
			}
		});

		super.onUpdateListener(eventId, messageIDs);
	}

    @Override
    public int getCurrentFilterType() {

        InboxFragment currentFragment = mInboxFragmentPagerAdapter.getFragment(mTabLayout.getSelectedTabPosition());
        return currentFragment.getFilterType();
    }

	/**
	 * closes the existing dialog and show a new one
	 *
	 * @param eventId
	 * @return
	 */
	private void showMaxMessagesDialog(int eventId) {
		// closes the existing dialog and show a new one
		if (maxMessagesDialog != null) {
			try {
				maxMessagesDialog.dismiss();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
			maxMessagesDialog = null;
		}

		int messageStringId = (eventId == DIALOG_MESSAGE_FULL) ? R.string.messagesFull
				: R.string.messagesAlmostFull;

		AlertDlgUtils.showDialog(InboxActivity.this, R.string.messagesFullTitle, messageStringId, R.string.ok_got_it_caps, 0, true, new AlertDlgUtils.AlertDlgInterface() {
            @Override
            public void handlePositiveButton(View view) {

            }

            @Override
            public void handleNegativeButton(View view) {

            }
        });

	}

    @Override
    protected void refreshUi() {

		boolean isGroupedByContact = ModelManager.getInstance().isGroupByContact();
        Logger.d(TAG, "refreshUi isGroupedByContact=" + isGroupedByContact);

        refreshFragments(isGroupedByContact);
		if (!Utils.isNetworkAvailable()) {
			Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
			stopGauge();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		Logger.d(TAG, "onPause");

		// unregisters the screen from getting model notifications
		modelManager.removeEventListener(this);

		super.onPause();
	}

	@Override
	protected void onStop() {

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Logger.d(TAG, "onDestroy");

		// remove listeners
		OperationsQueue.getInstance().removeEventListener(this);
		ContactsContentObserver.getInstance().removeEventListener(this, this);

		super.onDestroy();
	}

	/**
	 *
	 */
	@Override
	public void onBackPressed() {
		Logger.d(TAG, "onBackPressed()");

		// sets the result as finished for the welcome activity to be closed
		setResult(EVENTS.INBOX_FINISHED);
		finish();
	}

	@Override
	public void onNetworkFailure() {
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {

		Logger.d(TAG, "onWindowFocusChanged() hasFocus = " + hasFocus);

		// once the inbox screen is displyed for the first time since we started
		// the application -
		if (!modelManager.wasMailboxRefreshedOnStartup()) {
			Logger.d(TAG, "InboxActivity.onWindowFocusChanged() going to refresh inbox");
			refreshInbox();
		}
		super.onWindowFocusChanged(hasFocus);
	}

	//	/**
//	 * Show waiting animation
//	 *
	private void startGauge() {
		mInboxFragmentPagerAdapter.startGauge();
	}


	/**
	 * Stop waiting animation
	 */
	private void stopGauge() {
		mInboxFragmentPagerAdapter.stopGauge();
	}

    @Override
    protected void goToInboxSavedTab() {

        mTabLayout.getTabAt(InboxFragmentPagerAdapter.sSavedInboxItemsTabIndex).select();

    }

    @Override
    protected void closeEditMode() {

        Logger.d(TAG, "closeEditMode ");
        InboxFragment currentFragment = mInboxFragmentPagerAdapter.getFragment(mTabLayout.getSelectedTabPosition());
        currentFragment.closeEditMode();
    }

    @Override
    public void onListUpdated(int type, int size) {

        Logger.d(TAG, "onListUpdated type=" + type + " size=" + size);
        InboxFragment currentFragment = mInboxFragmentPagerAdapter.getFragment(mTabLayout.getSelectedTabPosition());
        if ( currentFragment.getFilterType() == type ) {
            setToolBarPlayAllBtn(size != 0);
        }
    }

}

