package com.castles.remote.core;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.castles.remote.RemoteService;

import java.io.Closeable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class DesktopConnection implements Closeable {

    private static final int DEVICE_NAME_FIELD_LENGTH = 64;

    private final Socket videoSocket;
    private final Socket controlSocket;
    private final InputStream controlInputStream;
    private final OutputStream controlOutputStream;
    private final OutputStream videoOutputStream;
    private final ControlMessageReader reader = new ControlMessageReader();
    private final DeviceMessageWriter writer = new DeviceMessageWriter();

    private DesktopConnection(Socket videoSocket, Socket controlSocket, byte[] buffer) throws IOException {
        this.videoSocket = videoSocket;
        this.controlSocket = controlSocket;
        controlInputStream = controlSocket.getInputStream();
        controlOutputStream = controlSocket.getOutputStream();
        videoOutputStream = videoSocket.getOutputStream();

        buffer[0] = 0;
        buffer[1] = 1;
        IO.writeStreamFully(videoOutputStream, buffer, 0, 15);
        buffer[1] = 2;
        IO.writeStreamFully(controlOutputStream, buffer, 0, 15);
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    public static Socket connect(String host, int port) throws IOException {
        return new Socket(host, port);
    }

    /**
     * 4.根据tunnelForward参数进行发送端和接收端的服务进行连接
     *
     * @param device
     * @param tunnelForward
     * @param host
     * @return
     * @throws IOException
     */
    public static DesktopConnection open(int port, Device device, boolean tunnelForward,String ip, String host, byte[] buffer) throws IOException {
        Socket videoSocket = null;
        Socket controlSocket = null;
        if (tunnelForward) {
            ServerSocket serverSocket = new ServerSocket(port, 2, InetAddress.getByName(ip));
            try {
                //创建一个服务端的socket
                videoSocket = serverSocket.accept();

                Ln.d("DesktopConnection open videoSocket:" + videoSocket);
                // send one byte so the client may read() to detect a connection error
                if (videoSocket != null) {
                    videoSocket.getOutputStream().write(0);
                }
                try {
                    controlSocket = serverSocket.accept();
                } catch (IOException | RuntimeException e) {
                    if (videoSocket != null) {
                        videoSocket.close();
                    }
                    throw e;
                }
            } finally {
                serverSocket.close();
            }
        } else {
            videoSocket = connect(host, port);
            try {
                controlSocket = connect(host, port);
            } catch (IOException | RuntimeException e) {
                videoSocket.close();
                throw e;
            }
        }

        DesktopConnection connection = new DesktopConnection(videoSocket, controlSocket, buffer);
        Size videoSize = device.getScreenInfo().getVideoSize();
        Ln.d("DesktopConnection send DeviceName:" + Device.getDeviceName());
        Ln.d("DesktopConnection send videoWidth:" + videoSize.getWidth());
        Ln.d("DesktopConnection send videoHeight:" + videoSize.getHeight());
        connection.send(Device.getDeviceName(), videoSize.getWidth(), videoSize.getHeight());
        return connection;
    }

    @Override
    public void close() {
        try {
            videoSocket.shutdownInput();
            videoSocket.shutdownOutput();
            videoSocket.close();
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
            controlSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            RemoteService.setIsStarted(false);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void send(String deviceName, int width, int height) throws IOException {
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH + 4];

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = StringUtils.getUtf8TruncationIndex(deviceNameBytes, DEVICE_NAME_FIELD_LENGTH - 1);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly

        buffer[DEVICE_NAME_FIELD_LENGTH] = (byte) (width >> 8);
        buffer[DEVICE_NAME_FIELD_LENGTH + 1] = (byte) width;
        buffer[DEVICE_NAME_FIELD_LENGTH + 2] = (byte) (height >> 8);
        buffer[DEVICE_NAME_FIELD_LENGTH + 3] = (byte) height;
//        IO.writeFully(videoFd, buffer, 0, buffer.length);
        IO.writeStreamFully(videoOutputStream, buffer, 0, buffer.length);
    }

    public OutputStream getVideoOutputStream() {
        return videoOutputStream;
    }

    public ControlMessage receiveControlMessage() throws IOException {
        ControlMessage msg = reader.next();
        while (msg == null) {
            reader.readFrom(controlInputStream);
            msg = reader.next();
        }
        return msg;
    }

    public void sendDeviceMessage(DeviceMessage msg) throws IOException {
        writer.writeTo(msg, controlOutputStream);
    }
}
