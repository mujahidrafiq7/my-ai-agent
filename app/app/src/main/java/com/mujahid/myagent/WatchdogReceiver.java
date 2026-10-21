package com.mujahid.myagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** v88: Watchdog alarm ka receiver — sirf A11yWatchdog.check chalata hai. */
public class WatchdogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        A11yWatchdog.check(ctx);
    }
}
