package com.castles.remote.core;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public final class IO {
    private IO() {
        // not instantiable
    }

    public static void writeFully(FileDescriptor fd, ByteBuffer from) throws IOException {
        // ByteBuffer position is not updated as expected by Os.write() on old Android versions, so
        // count the remaining bytes manually.
        int remaining = from.remaining();
        while (remaining > 0) {
            try {
                int w = Os.write(fd, from);
//                if (BuildConfig.DEBUG && w < 0) {
                  if (w < 0) {
                    // w should not be negative, since an exception is thrown on error
                    throw new AssertionError("Os.write() returned a negative value (" + w + ")");
                }
                remaining -= w;
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.EINTR) {
                    throw new IOException(e);
                }
            }
        }
    }

    public static void writeFully(FileDescriptor fd, byte[] buffer, int offset, int len) throws IOException {
        writeFully(fd, ByteBuffer.wrap(buffer, offset, len));
    }

    public static void writeStreamFully(OutputStream outputStream, ByteBuffer from) throws IOException {
        try {
//            outputStream.write(from.array(), 0, from.array().length);
            WritableByteChannel channel = Channels.newChannel(outputStream);
            channel.write(from);
            channel = null;
        } catch (IOException e) {
            throw e;
        }
    }

    public static void writeStreamFully(OutputStream outputStream, byte[] buffer, int offset, int len) throws IOException {
        writeStreamFully(outputStream, ByteBuffer.wrap(buffer, offset, len));
    }
}
