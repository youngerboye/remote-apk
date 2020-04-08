package com.castles.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RemoteReceiver extends BroadcastReceiver {
    private static final String TAG = "RemoteReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        Log.d(TAG,"onReceive:" + action);
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
//            Intent startServiceIntent = new Intent(context, RemoteService.class);
//            context.startService(startServiceIntent);
        }
    }
}
