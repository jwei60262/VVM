package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.widget.Toast;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.util.ArrayList;

/**
 * The PreferencesActivity class is the settings Activity for the application
 *
 * @author Mark Koltnuk
 */
public class PreferencesActivity extends VVMActivity implements EventListener
{

    private Boolean isCanceled = false;
    private static final String TAG = "PreferencesActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        Logger.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences);

        initActionBar(R.string.settings_menu_title, true);

        this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.settings_menu_title));

        getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, new CustomPreferenceFragment()).commit();
    }

    public static class CustomPreferenceFragment extends PreferenceFragmentCompat
    {

        private Preference greetingTypePref = null;
        private Preference changePassPref = null;

        @Override
        public void onCreatePreferences(Bundle bundle, String s)
        {
            //add xml
            addPreferencesFromResource(R.xml.preferences);

            greetingTypePref = findPreference(getString(R.string.pref_GreetingsKey));
            changePassPref = findPreference(getString(R.string.pref_ChangePwdKey));

            greetingTypePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    //we have data connection
                    if (Utils.isNetworkAvailable())
                    {
                        Logger.d(TAG, "greetingTypePref.onPreferenceClick()");
                        PreferencesActivity parent = (PreferencesActivity) getActivity();
                        if (parent != null && !parent.isFinishing())
                        {
                            parent.getGreetingsDetails();
                        }
                    }
                    else
                    {
                        Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
                    }
                    return true;
                }
            });

            changePassPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
            {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Logger.d(TAG, "changePassPref.onPreferenceClick");

                    // we have data connection
                    if (Utils.isNetworkAvailable())
                    {
                        PreferencesActivity parent = (PreferencesActivity) getActivity();
                        if (parent != null && !parent.isFinishing())
                        {
                            parent.openChangePasswordScreen();
                        }
                    }
                    else
                    {
                        Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);

                    }
                    return true;

                }
            });
        }
    }

    /**
     * Gets greetings details from the server.
     */
    private void getGreetingsDetails()
    {
        Logger.d(TAG, "getGreetingsDetails");

        if (!SimManager.getInstance(this).validateSim().isSimPresentAndReady())
        {
            showNoSimDialog();
            return;
        }
        showGauge("");
        // enqueues a get greetings details operation to the operations queue
        OperationsQueue.getInstance().enqueueGetGreetingsDetailsOperation();
    }

    private void openChangePasswordScreen()
    {
        if (!SimManager.getInstance(this).validateSim().isSimPresentAndReady())
        {
            showNoSimDialog();
            return;
        }
        try
        {
            Intent i = new Intent(PreferencesActivity.this, ChangePasswordActivity.class);
            i.putExtra(ChangePasswordActivity.FROM_SETTINGS, true);
            startActivityForResult(i, 0);
        }
        catch (Exception e)
        {
            Logger.e(TAG, e.getMessage(), e);
        }
    }

    private void showNoSimDialog()
    {
        Logger.d(TAG, "openChangePasswordScreen(): no sim card / sim not ready. Cannot refresh inbox.");
        AlertDlgUtils.showDialog(this, 0, R.string.no_sim_dialog_body_text,
                R.string.no_sim_dialog_positive_button_text, 0, false, null);
    }

    @Override
    protected void onResume()
    {
        Logger.d(TAG, "onResume");

        super.onResume();

        if (mustBackToSetupProcess())
        {
            return;
        }

        ((VVMApplication) getApplicationContext()).setVisible(true);
        OperationsQueue.getInstance().addEventListener(this);
        ModelManager.getInstance().addEventListener(this);
    }

    @Override
    protected void onPause()
    {
        Logger.d(TAG, "onPause");
        super.onPause();
        ((VVMApplication) getApplicationContext()).setVisible(false);
        ModelManager.getInstance().removeEventListener(this);
        OperationsQueue.getInstance().removeEventListener(this);
    }

    @Override
    protected void onDestroy()
    {
        Logger.d(TAG, "onDestroy");

        super.onDestroy();
    }

    @Override
    public void onBackPressed()
    {
        Logger.d(TAG, "onBackPressed");
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        Logger.d(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

        // in case that the password was changed
        if (resultCode == EVENTS.PASSWORD_CHANGE_FINISHED)
        {
            // show success message
            Utils.showToast(R.string.password_changed, Toast.LENGTH_LONG);
        }
        // in case that the password was NOT changed
        else if (resultCode == EVENTS.PASSWORD_CHANGE_FAILED)
        {
            // show failed message
            Utils.showToast(R.string.password_not_changed, Toast.LENGTH_LONG);

        }
    }

    @Override
    public void onUpdateListener(final int eventId, ArrayList<Long> messageIDs)
    {
        // holds the context as final value for later use
        final Context context = this;

        handler.post(new Runnable()
        {
            @Override
            public void run()
            {
                // in case getting greetings meta data was cancelled by the user, do nothing
                if (isCanceled)
                {
                    return;
                }

                switch (eventId)
                {

                    case EVENTS.GET_METADATA_GREETING_FAILED:
                        Logger.d(TAG, "onUpdateListener() GET_METADATA_GREETING_FAILED");
                        // stop loading animation
                        dismissGauge();
                        break;

                    case EVENTS.GET_METADATA_PASSWORD_FINISHED:
                        Logger.d(TAG, "onUpdateListenerForMessages - [GET_METADATA_PASSWORD_FINISHED]");
                        break;

                    case EVENTS.GET_METADATA_GREETING_DETAILS_FINISHED:
                        Logger.d(TAG, "onUpdateListener() GET_METADATA_GREETING_DETAILS_FINISHED");
                        if (ModelManager.getInstance().getMetadata() != null)
                        {
                            // enqueues a get existing greetings operations to the operations queue
                            OperationsQueue.getInstance().enqueueGetExistingGreetingsOperation();
                            return;
                        }
                        break;

                    case EVENTS.GET_METADATA_EXISTING_GREETINGS_FINISHED:
                        Logger.d(TAG, "onUpdateListener() GET_METADATA_EXISTING_GREETINGS_FINISHED");
                        // stops loading animation
                        dismissGauge();
                        OperationsQueue.getInstance().removeEventListener((PreferencesActivity) context);
                        // start the greetings screen
                        Intent i = new Intent(context, GreetingActionsActivity.class);
                        startActivity(i);
                        break;

                    case EVENTS.LOGIN_FAILED:
                        Logger.d(TAG, "onUpdateListener() LOGIN_FAILED");

                        // stop loading animation
                        dismissGauge();
                        Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);


                        break;

                    case EVENTS.START_WELCOME_ACTIVITY:
                        Logger.d(TAG, "onUpdateListener() START_WELCOME_ACTIVITY");
                        // show welcome screen if application is in foreground
                        if (((VVMApplication) getApplicationContext()).isVisible())
                        {
                            startActivity(new Intent((PreferencesActivity) context, WelcomeActivity.class));
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void onNetworkFailure()
    {
        // TODO Auto-generated method stub

    }
}