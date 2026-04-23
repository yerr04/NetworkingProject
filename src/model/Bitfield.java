package model;

public final class Bitfield {
    private final int numPieces;
    private final boolean[] have;

    public Bitfield(int numPieces) {
        if (numPieces <= 0) throw new IllegalArgumentException("numPieces must be positive");
        this.numPieces = numPieces;
        this.have = new boolean[numPieces];
    }

    public synchronized void setAll() {
        for (int i = 0; i < numPieces; i++) have[i] = true;
    }

    public synchronized void set(int index) {
        checkIndex(index);
        have[index] = true;
    }

    public synchronized boolean has(int index) {
        checkIndex(index);
        return have[index];
    }

    public synchronized int count() {
        int c = 0;
        for (boolean h : have) if (h) c++;
        return c;
    }

    public synchronized boolean isComplete() {
        for (boolean h : have) if (!h) return false;
        return true;
    }

    public int numPieces() {
        return numPieces;
    }

    public synchronized byte[] toBytes() {
        int numBytes = (numPieces + 7) / 8;
        byte[] out = new byte[numBytes];
        for (int i = 0; i < numPieces; i++) {
            if (have[i]) {
                int byteIdx = i / 8;
                int bitIdx = 7 - (i % 8);
                out[byteIdx] |= (byte) (1 << bitIdx);
            }
        }
        return out;
    }

    public synchronized void fromBytes(byte[] bytes) {
        for (int i = 0; i < numPieces; i++) {
            int byteIdx = i / 8;
            int bitIdx = 7 - (i % 8);
            if (byteIdx < bytes.length) {
                have[i] = (bytes[byteIdx] & (1 << bitIdx)) != 0;
            } else {
                have[i] = false;
            }
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= numPieces) {
            throw new IndexOutOfBoundsException("piece index: " + index);
        }
    }
}