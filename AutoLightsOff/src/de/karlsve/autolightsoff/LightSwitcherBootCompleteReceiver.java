package de.karlsve.autolightsoff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LightSwitcherBootCompleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, LightSwitcher.class));
    }

}
