package com.att.mobile.android.infra.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import com.att.mobile.android.vvm.VVMApplication;

import java.util.ArrayList;

/**
 * Created by evinouze on 07/03/2016.
 */
public class PermissionUtils {

    private static final String TAG = "PermissionUtils";
    public static String[] REQUIRED_PERMISSIONS = { Manifest.permission.READ_PHONE_STATE,
                                                    Manifest.permission.SEND_SMS,
                                                    Manifest.permission.READ_CONTACTS};


    public static boolean isPermissionGranted(String permission) {
        return ContextCompat.checkSelfPermission(VVMApplication.getContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isMissingPermission(String permission) {
        return ContextCompat.checkSelfPermission(VVMApplication.getContext(), permission) == PackageManager.PERMISSION_DENIED;
    }

    /**
     * Please note this method will rerun false positive in case it is called before ever requesting the permission
     * @param permission
     * @param activity
     * @return
     */
    @TargetApi(23)
    public static boolean wasNeverAskAgainChecked(String permission, Activity activity) {
        return !activity.shouldShowRequestPermissionRationale(permission);
    }

    public static  boolean areRequiredPermissionsGranted() {
        ArrayList<String> neededPermissions = new ArrayList<String>();

        for (int i = 0; i < REQUIRED_PERMISSIONS.length; i++){
            if (isMissingPermission(REQUIRED_PERMISSIONS[i])){
                neededPermissions.add(REQUIRED_PERMISSIONS[i]);
            }
        }
        if (neededPermissions.size() > 0) {
            Logger.d(TAG, "#areRequiredPermissionsGranted missing permissions");
            return false;
        } else {
            Logger.d(TAG, "#areRequiredPermissionsGranted all permissions granted");
            return true;
        }
    }

    public static String[] getNeededPermission(){
        ArrayList<String> neededPermissions = new ArrayList<String>();

        for (int i = 0; i < REQUIRED_PERMISSIONS.length; i++){
            if (isMissingPermission(REQUIRED_PERMISSIONS[i])){
                Logger.d(TAG, "#getNeededPermission missing permission " + REQUIRED_PERMISSIONS[i]);
                neededPermissions.add(REQUIRED_PERMISSIONS[i]);
            }
        }
        if (neededPermissions.size() > 0) {
            return (String[]) neededPermissions.toArray(new String[neededPermissions.size()]);
        }
        return null;
    }
}
