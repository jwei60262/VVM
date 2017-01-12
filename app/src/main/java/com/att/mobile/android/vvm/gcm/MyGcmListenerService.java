package com.att.mobile.android.vvm.gcm;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.gson.Gson;

import java.util.ArrayList;

public class MyGcmListenerService extends GcmListenerService
{
    private static final String TAG = MyGcmListenerService.class.getSimpleName();

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    @Override
    public void onMessageReceived(String from, Bundle data)
    {
        //FCM Handling start
//        Bundle bundle = data.getParcelable("notification");
//        String message = bundle.getString("body");
        //FCM Handling end

        String message = data.getString("message");

        Log.d(TAG, "From: " + from);
        Log.d(TAG, "Message: " + message);

        try
        {
            GcmMessage gcmMessage = new Gson().fromJson(message, GcmMessage.class);

            ArrayList<String> eventTypes = gcmMessage.getEventTypes();

            for(String eventType : eventTypes)
            {
                GcmEventFactory.getEventCommand(eventType).run();
            }
        }
        catch(Exception e)
        {
            Log.d(TAG, "Failed to process GCM message: " + e.getMessage());
        }
    }
}
