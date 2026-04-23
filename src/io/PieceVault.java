package io;

import model.CommonState;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class PieceVault implements AutoCloseable {
    private final CommonState commonState;
    private final RandomAccessFile raf;

    public PieceVault(CommonState commonState, File peerDir, String fileName, boolean seeder) throws IOException {
        if (commonState == null) throw new IllegalArgumentException("commonState is null");
        if (peerDir == null) throw new IllegalArgumentException("peerDir is null");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName empty");
        this.commonState = commonState;

        File file = new File(peerDir, fileName);
        if (seeder) {
            if (!file.exists()) {
                throw new IOException("Seeder file missing at startup: " + file.getPath());
            }
            if (file.length() < commonState.fileSize()) {
                throw new IOException("Seeder file shorter than FileSize: " + file.getPath());
            }
        } else if (!file.exists() || file.length() != commonState.fileSize()) {
            try (RandomAccessFile init = new RandomAccessFile(file, "rw")) {
                init.setLength(commonState.fileSize());
            }
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public int lengthOf(int index) {
        int n = commonState.numPieces();
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException("piece: " + index);
        if (index == n - 1) {
            int lastLen = commonState.fileSize() - commonState.pieceSize() * (n - 1);
            return lastLen == 0 ? commonState.pieceSize() : lastLen;
        }
        return commonState.pieceSize();
    }

    public synchronized byte[] loadPiece(int index) throws IOException {
        byte[] buf = new byte[lengthOf(index)];
        raf.seek((long) index * commonState.pieceSize());
        raf.readFully(buf);
        return buf;
    }

    public synchronized void storePiece(int index, byte[] content) throws IOException {
        if (content == null) throw new IllegalArgumentException("content is null");
        int expected = lengthOf(index);
        if (content.length != expected) {
            throw new IOException("Piece " + index + " has length " + content.length
                    + " but expected " + expected);
        }
        raf.seek((long) index * commonState.pieceSize());
        raf.write(content);
    }

    @Override
    public synchronized void close() throws IOException {
        raf.close();
    }
}
