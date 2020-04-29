package com.castles.remote;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.app.Service;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.castles.remote.core.Server;
import com.castles.remote.core.constants.AgentConstants;

public class RemoteService extends Service {
    private static final String TAG = "RemoteService";
    private static boolean isStarted = false;

    public static void setIsStarted(boolean isStarted) {
        RemoteService.isStarted = isStarted;
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: " + msg);
            switch (msg.what) {
                case 1:
                    int id = (int) msg.obj;
                    Toast.makeText(getApplicationContext(), "id:" + id, Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    int message = (int) msg.obj;
                    resolveMessage(message);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    private void resolveMessage(int message) {
        switch (message) {
            case 1:
                Toast.makeText(getApplicationContext(), "WIFI DISCONNECTED!!!", Toast.LENGTH_SHORT).show();
                break;
            case 2:
            case 3:
                Toast.makeText(getApplicationContext(), "Start Error! Try again!", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(getApplicationContext(), "UNKOWN", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "============onStartCommand start isStarted:" + isStarted);
        if (isStarted) {
            return START_NOT_STICKY;
        }
        //todo 从remote agent获取五个参数
        String agentIp = intent.getStringExtra(AgentConstants.AGENT_IP);
        String agentPort = intent.getStringExtra(AgentConstants.AGENT_PORT);
        String agentSerialNumber = intent.getStringExtra(AgentConstants.AGENT_SERIAL_NUMBER);
        String manufacture = intent.getStringExtra(AgentConstants.MANUFACTURE);
        String deviceModel = intent.getStringExtra(AgentConstants.DEVICE_MODEL);
        Log.d(TAG, "agentIp: " + agentIp);
        Log.d(TAG, "agentPort: " + agentPort);
        Log.d(TAG, "agentSerialNumber: " + agentSerialNumber);
        Log.d(TAG, "manufacture: " + manufacture);
        Log.d(TAG, "deviceModel: " + deviceModel);

        isStarted = true;
        new Thread(() -> {
            try {
                Server.start(RemoteService.this, handler,agentIp,agentPort,agentSerialNumber,manufacture,deviceModel);
                isStarted = false;
            } catch (Exception e) {
                Log.d(TAG, "Could not start core Server:" + e.getMessage());
                e.printStackTrace();
            }
        }).start();
//        isStarted = true;
//        new Thread(() -> {
//            try {
//                Server.start(RemoteService.this, handler);
//                isStarted = false;
//            } catch (Exception e) {
//                Log.d(TAG, "Could not start core Server:" + e.getMessage());
//                e.printStackTrace();
//            }
//        }).start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "============onBind==========");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "============onCreate==========");
//        Intent intent = new Intent(this, MainActivity.class);
//        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
//        builder.setContentIntent(pi);
//        // 【适配Android8.0】设置Notification的Channel_ID,否则不能正常显示
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            builder.setChannelId("notification_id");
//        }
//        // 额外添加：
//        // 【适配Android8.0】给NotificationManager对象设置NotificationChannel
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        // 启动前台服务通知
//        startForeground(1, builder.build());
    }

    @Override
    public void onDestroy() {
        isStarted = false;
        super.onDestroy();
        Log.d(TAG, "============onDestroy==========");
    }
}
