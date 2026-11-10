package com.maxassistant.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.maxassistant.app.data.AppStore;

/** Boot restores OS-scheduled work after first unlock; never starts a microphone. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try (AppStore store = new AppStore(context)) {
                store.recoverRunningTasks();
                if (store.prefs().getBoolean("bg_enabled", false)) TaskService.schedule(context);
            }
        }
    }
}
