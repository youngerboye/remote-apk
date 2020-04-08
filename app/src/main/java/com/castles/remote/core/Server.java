package com.castles.remote.core;

import android.graphics.Rect;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;

public final class Server {

    private static Context serviceContext;
    private static Handler serviceHandler;

    private Server() {
        // not instantiable
    }

    /**
     * 3.类似于scrcpy第三步进入scrcpy方法
     * @param options
     * @param host
     * @return
     * @throws IOException
     */
    private static Controller castleRemote(Options options, String host) throws IOException {
        //初始化设备管理器
        final Device device = new Device(options);
        Controller controller = null;
        //根据tunnelForward的值来创建连接
        boolean tunnelForward = options.isTunnelForward();
        try (DesktopConnection connection = DesktopConnection.open(device, tunnelForward, host)) {
            ScreenEncoder screenEncoder = new ScreenEncoder(options.getSendFrameMeta(), options.getBitRate());

            // 根据Control参数确认是否能对设备进行操作，如按键、鼠标等事件的响应
            if (options.getControl()) {
                controller = new Controller(device, connection);

                // asynchronous
                startController(controller);
                startDeviceMessageSender(controller.getSender());
            }

            try {
                // synchronous
                //录屏和编码
                screenEncoder.streamScreen(device, connection.getVideoOutputStream());
            } catch (IOException e) {
                // this is expected on close
                Ln.d("Screen streaming stopped");
            }
        }
        Ln.d("Server castleRemote exit!!!!!");
        return controller;
    }

    private static void startController(final Controller controller) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    controller.control();
                } catch (IOException e) {
                    // this is expected on close
                    Ln.d("Controller stopped");
                }
            }
        }).start();
    }

    private static void startDeviceMessageSender(final DeviceMessageSender sender) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sender.loop();
                } catch (IOException | InterruptedException e) {
                    // this is expected on close
                    Ln.d("Device message sender stopped");
                }
            }
        }).start();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Options createOptions(String... args) {
        if (args.length != 6) {
            throw new IllegalArgumentException("Expecting 6 parameters");
        }

        Options options = new Options();

        int maxSize = Integer.parseInt(args[0]) & ~7; // multiple of 8
        options.setMaxSize(maxSize);
        Ln.d("createOptions maxSize:" + maxSize);

        int bitRate = Integer.parseInt(args[1]);
        options.setBitRate(bitRate);
        Ln.d("createOptions bitRate:" + bitRate);

        // use "adb forward" instead of "adb tunnel"? (so the server must listen)
        boolean tunnelForward = Boolean.parseBoolean(args[2]);
        options.setTunnelForward(tunnelForward);
        Ln.d("createOptions tunnelForward:" + tunnelForward);

        Rect crop = parseCrop(args[3]);
        options.setCrop(crop);

        boolean sendFrameMeta = Boolean.parseBoolean(args[4]);
        options.setSendFrameMeta(sendFrameMeta);
        Ln.d("createOptions sendFrameMeta:" + sendFrameMeta);

        boolean control = Boolean.parseBoolean(args[5]);
        options.setControl(control);
        Ln.d("createOptions control:" + control);

        return options;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Rect parseCrop(String crop) {
        if ("-".equals(crop)) {
            return null;
        }
        // input format: "width:height:x:y"
        String[] tokens = crop.split(":");
        if (tokens.length != 4) {
            throw new IllegalArgumentException("Crop must contains 4 values separated by colons: \"" + crop + "\"");
        }
        int width = Integer.parseInt(tokens[0]);
        int height = Integer.parseInt(tokens[1]);
        int x = Integer.parseInt(tokens[2]);
        int y = Integer.parseInt(tokens[3]);
        return new Rect(x, y, x + width, y + height);
    }
/*
    private static void unlinkSelf() {
        try {
            new File(SERVER_PATH).delete();
        } catch (Exception e) {
            Ln.e("Could not unlink server", e);
        }
    }
*/

    public static String int2ip(int ipInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(ipInt & 0xFF).append(".");
        sb.append((ipInt >> 8) & 0xFF).append(".");
        sb.append((ipInt >> 16) & 0xFF).append(".");
        sb.append((ipInt >> 24) & 0xFF);
        return sb.toString();
    }

    /**
     * 类似于scrcpy的main方法
     * @param context
     * @param args
     * @throws Exception
     */
    public static void startConnect(Context context, String... args) throws Exception {
//        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
//            @Override
//            public void uncaughtException(Thread t, Throwable e) {
//                Ln.e("Exception on thread " + t, e);
//            }
//        });

//        unlinkSelf();

        boolean started = false;
        NetworkInfo.State state;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        while (true) {
            state = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
            if (state != NetworkInfo.State.CONNECTED) {
                Ln.d("WIFI DISCONNECTED!!!");
            } else if (state == NetworkInfo.State.CONNECTED) {
                Ln.d("WIFI CONNECTED!!!");
            }
            Controller controller = null;
            if (!started && (state == NetworkInfo.State.CONNECTED)) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int i = wifiInfo.getIpAddress();
                String ip = int2ip(i);
                Ln.d(" ip:" + ip);
                //对尺寸，码率，裁剪等参数进行解析，初始化options对象
                Options options = createOptions(args);
                try {
                    controller = castleRemote(options, ip);
                } catch (Exception e) {
                    Ln.e("castleRemote start", e);
                }
                started = true;
            }

            if (controller != null) {
                controller.stop();
                controller = null;
            }
            started = false;

            Thread.sleep(1000);
        }
    }

    /**
     * 1.点击start按钮
     * @param context
     * @param handler
     */
    public static void start(Context context, Handler handler) {
        serviceContext = context;
        serviceHandler = handler;
        NetworkInfo.State state;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        //获取网络连接状态
        state = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
        if (state != NetworkInfo.State.CONNECTED) {
            Message message = serviceHandler.obtainMessage();
            message.obj = 1;
            message.what = 2;
            serviceHandler.sendMessage(message);
            return;
        }

        try {
            //尝试建立一个socket连接
            Socket socket = DesktopConnection.connectServer();
            if (socket == null) {
                Message message = serviceHandler.obtainMessage();
                message.obj = 2;
                message.what = 2;
                serviceHandler.sendMessage(message);
            }
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            String deviceName = Device.getDeviceName();
            String deviceSerial = Device.getDeviceSerial();

            byte[] buffer = new byte[6 + 32 + 16];
            buffer[0] = 0;
            buffer[1] = 0;
            buffer[2] = 0;
            buffer[3] = 0;
            buffer[4] = 0;
            buffer[5] = 0;

            byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
            int len = StringUtils.getUtf8TruncationIndex(deviceNameBytes, 32);
            System.arraycopy(deviceNameBytes, 0, buffer, 6, len);

            byte[] deviceSerialBytes = deviceSerial.getBytes(StandardCharsets.UTF_8);
            len = StringUtils.getUtf8TruncationIndex(deviceSerialBytes, 16);
            System.arraycopy(deviceSerialBytes, 0, buffer, 6 + 32, len);
            IO.writeStreamFully(outputStream, buffer, 0, 6 + 32 + 16);

            byte[] idBuffer = new byte[4];
            int r = inputStream.read(idBuffer, 0, 4);
            if (r == -1) {
                throw new IOException("Connect socket closed");
            }
            Device.id = (idBuffer[0] << 24) + (idBuffer[1] << 16) + (idBuffer[2] << 8) + idBuffer[3];
            Ln.d("Device.id:" + Device.id);

            Message message = serviceHandler.obtainMessage();
            message.obj = Device.id;
            message.what = 1;
            serviceHandler.sendMessage(message);

            while (true) {
                byte[] cmdBuffer = new byte[2];
                int rl = inputStream.read(cmdBuffer, 0, 2);
                if (rl == -1) {
                    throw new IOException("Connect socket closed");
                }
                Ln.d("cmdBuffer[0]:" + cmdBuffer[0]);
                Ln.d("cmdBuffer[1]:" + cmdBuffer[1]);

                if ((cmdBuffer[0] == 0x10) && (cmdBuffer[1] == 0x03)) {
                    new Thread(new Runnable() {
                        public void run() {
                            try {
                                startConnect(serviceContext, "0", "8000000", "false", "-", "true", "true");
                            } catch (Exception e) {
                                Ln.d("Could not start core Server:" + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }

            }
        } catch (IOException e) {
            // this is expected on close
            Ln.d("start error!");
            e.printStackTrace();
            Message message = serviceHandler.obtainMessage();
            message.obj = 3;
            message.what = 2;
            serviceHandler.sendMessage(message);
        }
    }
}
