package com.castles.remote;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.app.Service;
import android.content.Intent;
import android.util.Log;
import com.castles.remote.core.Server;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;


public class RemoteService extends Service {
    private static final String TAG = "RemoteService";
    private WeakReference<RemoteService> remoteService = new WeakReference<>(RemoteService.this);
    private RemoteBinder remoteBinder;
    private boolean isStarted = false;

    public class RemoteBinder extends Binder {
        public void start() {
            Log.d(TAG, "============RemoteBinder start isStarted:"+isStarted);
            if(isStarted) {
                return;
            }
            isStarted = true;
            new Thread (new Runnable() {
                @Override
                public void run() {
                    try {
//                    Server.start(RemoteService.this, "0", "8000000", "true", "-", "true", "true");
                        Server.start(RemoteService.this, handler);
                        isStarted = false;
                    } catch (Exception e) {
                        Log.d(TAG, "Could not start core Server:"+e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        public RemoteService getRemoteService() {
            return remoteService.get();
        }
    }

    private RemoteBinder getRemoteBinder() {
        if (remoteBinder == null) {
            remoteBinder = new RemoteBinder();
        }
        return remoteBinder;
    }
    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    int id = (int) msg.obj;
                    if (callBacks != null && callBacks.size() > 0) {
                        for (CallBack callBack : callBacks) {
                            callBack.showID(id);
                        }
                    }
                    break;
                case 2:
                    int message = (int) msg.obj;
                    if (callBacks != null && callBacks.size() > 0) {
                        for (CallBack callBack : callBacks) {
                            callBack.postMessage(message);
                        }
                    }
            }
            super.handleMessage(msg);
        }
    };

    public interface CallBack {
        void showID(int id);
        void postMessage(int message);
    }

    private List<CallBack> callBacks = new LinkedList<>();

    public void registerCallBack(CallBack callBack) {
        if (callBacks != null) {
            callBacks.add(callBack);
        }
    }

    public boolean unRegisterCallBack(CallBack callBack) {
        if (callBacks != null && callBacks.contains(callBack)) {
            return callBacks.remove(callBack);
        }
        return false;
    }

    /*
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "============onStartCommand==========");
        new Thread (new Runnable() {
            public void run() {
                try {
                    Server.start(RemoteService.this, "0", "8000000", "false", "-", "true", "true");
                } catch (Exception e) {
                    Log.d(TAG, "Could not start core Server:"+e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }
*/

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "============onBind==========");
        return getRemoteBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "============onCreate==========");
    }

}
