package io;

import model.CommonState;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class PieceVault {
    private final CommonState commonState;
    private final File fullFilePath;
    private final byte[][] pieces;

    public PieceVault(CommonState commonState, File peerDir, String fileName) {
        if (commonState == null) throw new IllegalArgumentException("commonState is null");
        if (peerDir == null) throw new IllegalArgumentException("peerDir is null");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName empty");
        this.commonState = commonState;
        this.fullFilePath = new File(peerDir, fileName);
        this.pieces = new byte[commonState.numPieces()][];
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

    public void loadFromDisk() throws IOException {
        if (!fullFilePath.exists()) {
            throw new IOException("Complete file missing at startup: " + fullFilePath.getPath());
        }
        try (FileInputStream fis = new FileInputStream(fullFilePath)) {
            for (int i = 0; i < commonState.numPieces(); i++) {
                int len = lengthOf(i);
                byte[] piece = new byte[len];
                int read = 0;
                while (read < len) {
                    int r = fis.read(piece, read, len - read);
                    if (r < 0) throw new EOFException("Unexpected EOF reading " + fullFilePath.getPath());
                    read += r;
                }
                pieces[i] = piece;
            }
        }
    }

    public synchronized void storePiece(int index, byte[] content) {
        if (pieces[index] == null) pieces[index] = content;
    }

    public synchronized byte[] loadPiece(int index) {
        return pieces[index];
    }

    public synchronized void flushToDisk() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(fullFilePath)) {
            for (int i = 0; i < commonState.numPieces(); i++) {
                if (pieces[i] == null) {
                    throw new IOException("Missing piece " + i + " at write time");
                }
                fos.write(pieces[i]);
            }
        }
    }
}