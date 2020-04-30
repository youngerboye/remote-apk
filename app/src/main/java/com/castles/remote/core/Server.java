package com.castles.remote.core;

import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.castles.remote.RemoteService;

/**
 * @author Jackson
 */
public final class Server {

    private static Context serviceContext;
    private static Handler serviceHandler;

    private Server() {
    }

    /**
     * 3.类似于scrcpy第三步进入scrcpy方法
     *
     * @param options
     * @param host
     * @return
     * @throws IOException
     */
    private static Controller castleRemote(int port, Options options, String ip,String host, byte[] buffer) throws IOException {
        //初始化设备管理器
        final Device device = new Device(options);
        Controller controller = null;
        //根据tunnelForward的值来创建连接
        boolean tunnelForward = options.isTunnelForward();
        try (DesktopConnection connection = DesktopConnection.open(port, device, tunnelForward, ip, host, buffer)) {
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
        } catch (Exception e) {
            e.printStackTrace();
            RemoteService.setIsStarted(false);
        }
        Ln.d("Server castleRemote exit!!!!!");
        return controller;
    }

    private static void startController(final Controller controller) {
        new Thread(() -> {
            try {
                controller.control();
            } catch (IOException e) {
                // this is expected on close
                Ln.d("Controller stopped");
            }
        }).start();
    }

    private static void startDeviceMessageSender(final DeviceMessageSender sender) {
        new Thread(() -> {
            try {
                sender.loop();
            } catch (IOException | InterruptedException e) {
                // this is expected on close
                Ln.d("Device message sender stopped");
            }
        }).start();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static Options createOptions(String... args) {
        if (args.length != 8) {
            throw new IllegalArgumentException("Expecting 7 parameters");
        }
        Options options = new Options();
        // multiple of 8
        int maxSize = Integer.parseInt(args[0]) & ~7;
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
     *
     * @param context
     * @throws Exception
     */
    public static void startConnect(Context context, byte[] buffer, String... args) throws Exception {

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
            if (state == NetworkInfo.State.CONNECTED) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int i = wifiInfo.getIpAddress();
                String ip = int2ip(i);
                Ln.d(" ip:" + ip);
                int port = Integer.parseInt(args[6]);
                String host = args[7];
                //对尺寸，码率，裁剪等参数进行解析，初始化options对象
                Options options = createOptions(args);
                try {
                    controller = castleRemote(port, options, ip,host, buffer);
                } catch (Exception e) {
                    Ln.e("castleRemote start", e);
                }
            }

            if (controller != null) {
                controller.stop();
            }
            Thread.sleep(1000);
        }
    }

    /**
     * 1.点击start按钮
     *
     * @param context
     * @param handler
     */
    public static void start(Context context, Handler handler, String agentIp,
                             String agentPort, String agentSerialNumber,
                             String manufacture, String deviceModel) {
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
            Socket socket = DesktopConnection.connect(agentIp, Integer.parseInt(agentPort));

            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            byte[] buffer = new byte[2 + 1 + 3 + 1 + 4 + 1 + 3];
            buffer[0] = 0;
            buffer[1] = 0;

            byte[] agentSerialNumberBytes = agentSerialNumber.getBytes(StandardCharsets.US_ASCII);
            int len = StringUtils.getUtf8TruncationIndex(agentSerialNumberBytes, 3);
            byte[] serialNumberLengthBytes = toBytes(len);
            System.arraycopy(serialNumberLengthBytes, 0, buffer, 2, 1);
            System.arraycopy(agentSerialNumberBytes, 0, buffer, 2 + 1, len);

            byte[] manufactureBytes = manufacture.getBytes(StandardCharsets.US_ASCII);
            len = StringUtils.getUtf8TruncationIndex(manufactureBytes, 4);
            byte[] manufactureLengthBytes = toBytes(len);
            System.arraycopy(manufactureLengthBytes, 0, buffer, 2 + 1 + 3, 1);
            System.arraycopy(manufactureBytes, 0, buffer, 2 + 1 + 3 + 1, len);

            byte[] deviceModelBytes = deviceModel.getBytes(StandardCharsets.US_ASCII);
            len = StringUtils.getUtf8TruncationIndex(deviceModelBytes, 3);
            byte[] deviceModelLengthBytes = toBytes(len);
            System.arraycopy(deviceModelLengthBytes, 0, buffer, 2 + 1 + 3 + 1 + 4, 1);
            System.arraycopy(deviceModelBytes, 0, buffer, 2 + 1 + 3 + 1 + 4 + 1, len);

            Log.d("buffer", Arrays.toString(buffer));
            IO.writeStreamFully(outputStream, buffer, 0, 2 + 1 + 3 + 1 + 4 + 1 + 3);

            while (true) {
                byte[] cmdBuffer = new byte[2];
                int rl = inputStream.read(cmdBuffer, 0, 2);
                if (rl == -1) {
                    throw new IOException("Connect socket closed");
                }
                Ln.d("cmdBuffer[0]:" + cmdBuffer[0]);
                Ln.d("cmdBuffer[1]:" + cmdBuffer[1]);

                if ((cmdBuffer[0] == 0x10) && (cmdBuffer[1] == 0x03)) {
                    new Thread(() -> {
                        try {
                            startConnect(serviceContext, buffer, "2", "1500000", "false", "-", "true", "true", agentPort, agentIp);
                        } catch (Exception e) {
                            Ln.d("Could not start core Server:" + e.getMessage());
                            e.printStackTrace();
                        }
                    }).start();
                }
            }
        } catch (IOException e) {
            Ln.d("start error!");
            e.printStackTrace();
            Message message = serviceHandler.obtainMessage();
            message.obj = 3;
            message.what = 2;
            serviceHandler.sendMessage(message);
            RemoteService.setIsStarted(false);
        }
    }

    private static byte[] toBytes(int number) {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) number;
        return bytes;
    }
}
