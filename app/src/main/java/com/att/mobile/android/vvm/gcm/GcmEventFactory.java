package com.att.mobile.android.vvm.gcm;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;

import java.util.HashMap;

class GcmEventFactory
{
    private static final HashMap<String, Runnable> msEventsMap = new HashMap<String, Runnable>()
    {{
        put("VVM_MBOXUPDATE", new Runnable()
        {
            @Override
            public void run()
            {
                if(SimManager.getInstance(VVMApplication.getContext()).validateSim().isSimPresentAndReady())
                {
                    OperationsQueue.getInstance().enqueueFetchHeadersAndBodiesOperation();
                }
            }
        });
    }};

    static Runnable getEventCommand(String eventType)
    {
        return msEventsMap.get(eventType);
    }
}
