package com.att.mobile.android.infra.sim;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;

/**
 * Created by hginsburg on 4/25/2016.
 */
public class SimPhoneStateListener extends PhoneStateListener {

    private static final String TAG = "SimPhoneStateListener";

    @Override
    public void onServiceStateChanged(ServiceState serviceState) {
        Logger.d(TAG, "onServiceStateChanged()");
        SimManager.getInstance(VVMApplication.getContext()).startSimValidation();
    }
}
