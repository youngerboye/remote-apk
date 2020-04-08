package com.castles.remote.core;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.Closeable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class DesktopConnection implements Closeable {

    private static final int DEVICE_NAME_FIELD_LENGTH = 64;

    private static final String SOCKET_NAME = "CastleRemote";
    private static final String SOCKET_HOST = "192.168.10.18";
    private static final int SOCKET_PORT = 31415;

    private final Socket videoSocket;
//    private final FileDescriptor videoFd;

    private final Socket controlSocket;
    private final InputStream controlInputStream;
    private final OutputStream controlOutputStream;
    private final OutputStream videoOutputStream;

    private final ControlMessageReader reader = new ControlMessageReader();
    private final DeviceMessageWriter writer = new DeviceMessageWriter();

    private DesktopConnection(Socket videoSocket, Socket controlSocket) throws IOException {
        this.videoSocket = videoSocket;
        this.controlSocket = controlSocket;
        controlInputStream = controlSocket.getInputStream();
        controlOutputStream = controlSocket.getOutputStream();
        videoOutputStream = videoSocket.getOutputStream();

        byte[] buffer = new byte[6];
        buffer[0] = 0;
        buffer[1] = 1;
        buffer[2] = (byte)(Device.id>>24);
        buffer[3] = (byte)(Device.id>>16);
        buffer[4] = (byte)(Device.id>>8);
        buffer[5] = (byte)Device.id;
        IO.writeStreamFully(videoOutputStream, buffer, 0, 6);
        buffer[1] = 2;
        IO.writeStreamFully(controlOutputStream, buffer, 0, 6);
        //        videoFd = videoSocket.getFileDescriptor();
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        return socket;
    }

    /**
     * 4.根据tunnelForward参数进行发送端和接收端的服务进行连接
     * @param device
     * @param tunnelForward
     * @param host
     * @return
     * @throws IOException
     */
    public static DesktopConnection open(Device device, boolean tunnelForward, String host) throws IOException {
        Socket videoSocket = null;
        Socket controlSocket = null;
        if (tunnelForward) {
//            ServerSocket serverSocket = new ServerSocket();
            ServerSocket serverSocket = new ServerSocket(SOCKET_PORT, 2, InetAddress.getByName(host));
//            serverSocket.bind(new InetSocketAddress(host,SOCKET_PORT));
            try {
                //创建一个服务端的socket
                videoSocket = serverSocket.accept();
                
                Ln.d("DesktopConnection open videoSocket:"+videoSocket);
                // send one byte so the client may read() to detect a connection error
                if(videoSocket != null)
                {
                    videoSocket.getOutputStream().write(0);
                }
                try {
                    controlSocket = serverSocket.accept();
                } catch (IOException | RuntimeException e) {
                    if(videoSocket != null)
                    {
                        videoSocket.close();
                    }
                    throw e;
                }
            } finally {
                serverSocket.close();
            }
        } else {
            videoSocket = connect(SOCKET_HOST, SOCKET_PORT);
            try {
                controlSocket = connect(SOCKET_HOST, SOCKET_PORT);
            } catch (IOException | RuntimeException e) {
                videoSocket.close();
                throw e;
            }
        }

        DesktopConnection connection = new DesktopConnection(videoSocket, controlSocket);
        Size videoSize = device.getScreenInfo().getVideoSize();
        Ln.d("DesktopConnection send DeviceName:"+Device.getDeviceName());
        Ln.d("DesktopConnection send videoWidth:"+videoSize.getWidth());
        Ln.d("DesktopConnection send videoHeight:"+videoSize.getHeight());
        connection.send(Device.getDeviceName(), videoSize.getWidth(), videoSize.getHeight());
        return connection;
    }

    public void close() throws IOException {
        videoSocket.shutdownInput();
        videoSocket.shutdownOutput();
        videoSocket.close();
        controlSocket.shutdownInput();
        controlSocket.shutdownOutput();
        controlSocket.close();
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

//    public FileDescriptor getVideoFd() {
//        return videoFd;
//    }

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

    public static Socket connectServer() throws IOException {
        return connect(SOCKET_HOST, SOCKET_PORT);
    }
}
