package com.att.mobile.android.infra.utils;

import android.util.Log;

import com.att.mobile.android.vvm.VVMApplication;


public class Logger {
	//the global application tag name
	private static String APP_TAG =  "VVM_";
	
	public static int d(String tag, String msg) {
		if (VVMApplication.isDebugMode()){
			if(tag != null)
			{
				tag = APP_TAG+VVMApplication.getApplicationVersion()+"/"+tag;
			}
			return Log.d(tag, msg);
		}
		return 0;
    }
	
	public static int i(String tag, String msg) {
		if (VVMApplication.isDebugMode()){
			if(tag != null)
			{
				tag = APP_TAG+VVMApplication.getApplicationVersion()+"/"+tag;
			}
			return Log.i(tag, msg);
		}
		return 0;
    }

	public static int e(String tag, String msg, Throwable t) {
		if (VVMApplication.isDebugMode()){
			if(tag != null)
			{
				tag = APP_TAG+VVMApplication.getApplicationVersion()+"/"+tag;
			}
			return Log.e(tag, msg, t);
		}
		return 0;
	}
}
