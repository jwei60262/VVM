package com.att.mobile.android.vvm.gcm;

import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

class GcmMessage
{
    private static final String TAG = GcmMessage.class.getSimpleName();

    @SerializedName("eventList")
    private EventList mEventList;

    ArrayList<String> getEventTypes()
    {
        if(mEventList == null)
        {
            Log.e(TAG, "event list is null");
            return new ArrayList<>();
        }

        return mEventList.getEvents();
    }

    private class EventList
    {
        @SerializedName("date")
        private String mDate;

        @SerializedName("events")
        private ArrayList<Event> mEvents;

        public ArrayList<String> getEvents()
        {
            ArrayList<String> events = new ArrayList<>();

            if(mEvents == null)
            {
                Log.e(TAG, "events is null");
                return events;
            }

            for(Event event : mEvents)
            {
                if(event.getType() == null)
                {
                    Log.e(TAG, "event type is null");
                    continue;
                }

                events.add(event.getType());
            }

            return events;
        }

        private class Event
        {
            @SerializedName("type")
            private String mType;

            String getType()
            {
                return mType;
            }
        }
    }
}
