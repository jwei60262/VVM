package com.att.mobile.android.vvm.screen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;

@TargetApi(23)
public class PermissionsActivity extends AppCompatActivity {

    private String TAG = "PermissionsActivity";

    public static final int PERMISSIONS_REQUEST_CODE = 215;
    public static final int PERMISSION_DENIED_EXIT_APP = 300;
    public static final int PERMISSION_ACTIVITY_RESULT = 400 ;



    private InitialPermissionActivityFragment initialPermissionsFragment;
    private PermissionsGoToSettingsFragment permissionsGoToSettingsFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_activity);

    }

    @Override
    protected void onResume() {
        super.onResume();
        Logger.d(TAG, "onResume");
        if (!PermissionUtils.areRequiredPermissionsGranted()) {
            if(shouldShowPermissionsGoToSettingsFragment()) {
                goToPermissionsGoToSettingsFragment();
            } else {
                goToAllowPermissionsFragment();
            }
        }
        else {
            // we have all of the required permissions
            goToApp();
        }
    }

    private void goToApp() {
        Logger.d(TAG, "The User has granted permissions to continue with the app");
        setResult(Constants.EVENTS.PERMISSIONS_GRANTED);
        finish();
    }

    private boolean shouldShowPermissionsGoToSettingsFragment() {
        boolean permissionRequestedBefore = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PERMISSION_EVER_REQUESTED, Boolean.class,false);
        String[] needePermissions = PermissionUtils.getNeededPermission();

        // if the screen was seen before go over the permission list and check if 'Never ask again' was checked. Checked for  needePermissions != null as a precaution
        if(permissionRequestedBefore &&  needePermissions != null){
            for (int i=0 ;i<needePermissions.length ; i++){
                if(!shouldShowRequestPermissionRationale(needePermissions[i])){
                    // in case for one of the permissions 'Never ask again' was checked - go to PermissionsGoToSettingsFragment
                    return true;
                }
            }
        }
        return false;
    }

    public void goToAllowPermissionsFragment() {
        Logger.d(TAG, "goToInitialPermissionsFragment");

        FragmentManager supportFragmentManager = getSupportFragmentManager();
        initialPermissionsFragment = (InitialPermissionActivityFragment) supportFragmentManager.findFragmentByTag(InitialPermissionActivityFragment.TAG);
        if (initialPermissionsFragment == null) {
            initialPermissionsFragment = new InitialPermissionActivityFragment();
        }

        FragmentTransaction ft = supportFragmentManager.beginTransaction();
        ft.replace(R.id.current_permission_fragment, initialPermissionsFragment, InitialPermissionActivityFragment.TAG).commit();
    }

    public void goToPermissionsGoToSettingsFragment() {
        Logger.d(TAG, "goToPermissionsGoToSettingsFragment");

        FragmentManager supportFragmentManager = getSupportFragmentManager();
        permissionsGoToSettingsFragment = (PermissionsGoToSettingsFragment) supportFragmentManager.findFragmentByTag(PermissionsGoToSettingsFragment.TAG);
        if (permissionsGoToSettingsFragment == null) {
            permissionsGoToSettingsFragment = new PermissionsGoToSettingsFragment();
        }

        FragmentTransaction ft = supportFragmentManager.beginTransaction();
        ft.replace(R.id.current_permission_fragment, permissionsGoToSettingsFragment, PermissionsGoToSettingsFragment.TAG).commit();
    }

    public void exitAppWithoutPermissions() {
        setResult(PermissionsActivity.PERMISSION_DENIED_EXIT_APP);
        launchHome(this);
        finish();
    }
    private static void launchHome(Activity act) {
        Intent mHomeIntent = new Intent(Intent.ACTION_MAIN, null);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);
        mHomeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        act.startActivity(mHomeIntent);
    }
    @Override
    public void onBackPressed() {
        exitAppWithoutPermissions();
    }

    public void initTitleFontAndSize(){
        TextView title_att = (TextView)  findViewById(R.id.title_att);
        if(title_att != null) {
            title_att.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_RegularItalic));
            if (getResources().getConfiguration().fontScale >= 1.3f) {
                title_att.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
            }
        }

        TextView title_visual =(TextView)  findViewById(R.id.title_visual);
        if(title_visual != null) {
            title_visual.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_LightItalic));
            if (getResources().getConfiguration().fontScale >= 1.3f) {
                title_visual.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
            }
        }

    }
}
