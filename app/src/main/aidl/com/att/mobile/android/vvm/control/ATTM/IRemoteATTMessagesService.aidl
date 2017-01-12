package com.att.mobile.android.vvm.control.ATTM;

interface IRemoteATTMessagesService {

    boolean  isLegacyVVMUibCompatible();  
    String getVVMConnectivityCredentials (); 
    byte[] getFileFromMessage (String fileName);
   
   
}