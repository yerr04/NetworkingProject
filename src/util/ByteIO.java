package util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class ByteIO {
    private ByteIO() {}

    public static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int r = in.read(buf, off + read, len - read);
            if (r < 0) throw new EOFException("Unexpected EOF");
            read += r;
        }
    }

    public static int readInt(InputStream in) throws IOException {
        byte[] b = new byte[4];
        readFully(in, b, 0, 4);
        return bytesToInt(b, 0);
    }

    public static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) ((v >>> 24) & 0xFF),
                (byte) ((v >>> 16) & 0xFF),
                (byte) ((v >>> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    public static int bytesToInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }
}