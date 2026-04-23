package net;

import util.ByteIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class Frame {
    public static final byte CHOKE = 0;
    public static final byte UNCHOKE = 1;
    public static final byte INTERESTED = 2;
    public static final byte NOT_INTERESTED = 3;
    public static final byte HAVE = 4;
    public static final byte BITFIELD = 5;
    public static final byte REQUEST = 6;
    public static final byte PIECE = 7;

    public static final String HANDSHAKE_HEADER = "P2PFILESHARINGPROJ";
    public static final int HANDSHAKE_LEN = 32;

    public final byte type;
    public final byte[] payload;

    public Frame(byte type, byte[] payload) {
        this.type = type;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static void writeHandshake(OutputStream out, int selfPeerId) throws IOException {
        byte[] buf = new byte[HANDSHAKE_LEN];
        byte[] header = HANDSHAKE_HEADER.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, buf, 0, 18);
        byte[] id = ByteIO.intToBytes(selfPeerId);
        System.arraycopy(id, 0, buf, 28, 4);
        out.write(buf);
        out.flush();
    }

    public static int readHandshake(InputStream in) throws IOException {
        byte[] buf = new byte[HANDSHAKE_LEN];
        ByteIO.readFully(in, buf, 0, HANDSHAKE_LEN);
        String header = new String(buf, 0, 18, StandardCharsets.US_ASCII);
        if (!HANDSHAKE_HEADER.equals(header)) {
            throw new IOException("Invalid handshake header: " + header);
        }
        return ByteIO.bytesToInt(buf, 28);
    }

    public static byte[] buildFrame(byte type, byte[] payload) {
        if (payload == null) payload = new byte[0];
        int len = 1 + payload.length;
        byte[] framed = new byte[4 + 1 + payload.length];
        byte[] lenBytes = ByteIO.intToBytes(len);
        System.arraycopy(lenBytes, 0, framed, 0, 4);
        framed[4] = type;
        if (payload.length > 0) {
            System.arraycopy(payload, 0, framed, 5, payload.length);
        }
        return framed;
    }

    public static Frame readFrame(InputStream in) throws IOException {
        int len = ByteIO.readInt(in);
        if (len <= 0) throw new IOException("Invalid frame length: " + len);
        byte[] typeBuf = new byte[1];
        ByteIO.readFully(in, typeBuf, 0, 1);
        byte type = typeBuf[0];
        byte[] payload = new byte[len - 1];
        if (payload.length > 0) ByteIO.readFully(in, payload, 0, payload.length);
        return new Frame(type, payload);
    }
}