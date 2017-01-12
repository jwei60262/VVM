package com.att.mobile.android.infra.utils;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;


import com.att.mobile.android.vvm.VVMApplication;

import java.util.List;

/**
 * Created by azelitchenok on 13/10/2015.
 */
public class AccessibilityUtils {

    public static void sendEvent(String text, View view) {
        if(isAccessibilityActivated()){
            CharSequence tmp = view.getContentDescription();
            view.setContentDescription(text);
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            view.setContentDescription(tmp);
        }
    }
    public static void sendEvent( View view) {
        if(isAccessibilityActivated()){
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        }
    }


    public static boolean isAccessibilityActivated() {

        AccessibilityManager accessibilityManager = ((AccessibilityManager) VVMApplication.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE));
        if(accessibilityManager != null){
            List<AccessibilityServiceInfo> list = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
            if(list != null && !list.isEmpty()){
                return true;
            }
        }
        return false;
    }


}
