package model;

public final class CommonState {
    private final int fileSize;
    private final int pieceSize;
    private final int numPieces;

    public CommonState(int fileSize, int pieceSize) {
        if (fileSize <= 0) throw new IllegalArgumentException("FileSize must be positive");
        if (pieceSize <= 0) throw new IllegalArgumentException("PieceSize must be positive");
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.numPieces = (int) Math.ceil(fileSize / (double) pieceSize);
        if (numPieces <= 0) throw new IllegalStateException("Computed numPieces must be positive");
    }

    public int fileSize() {
        return fileSize;
    }

    public int pieceSize() {
        return pieceSize;
    }

    public int numPieces() {
        return numPieces;
    }
}
