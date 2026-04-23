package logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PeerLogger implements AutoCloseable {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final int selfPeerId;
    private final BufferedWriter writer;

    public PeerLogger(int selfPeerId, File workingDir) throws IOException {
        this.selfPeerId = selfPeerId;
        if (workingDir == null) throw new IllegalArgumentException("workingDir is null");
        if (!workingDir.exists()) throw new IOException("workingDir does not exist: " + workingDir.getPath());
        File logFile = new File(workingDir, "log_peer_" + selfPeerId + ".log");
        this.writer = new BufferedWriter(new FileWriter(logFile, true));
    }

    public void logRaw(String message) {
        String line = String.format("%s: %s%n", LocalDateTime.now().format(TS), message);
        synchronized (writer) {
            try {
                writer.write(line);
                writer.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed writing log", e);
            }
        }
    }

    public void logMakesConnectionTo(int peerId) {
        logRaw(String.format("Peer %d makes a connection to Peer %d.", selfPeerId, peerId));
    }

    public void logIsConnectedFrom(int peerId) {
        logRaw(String.format("Peer %d is connected from Peer %d.", selfPeerId, peerId));
    }

    public void logPreferredNeighbors(List<Integer> preferredIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < preferredIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(preferredIds.get(i));
        }
        logRaw(String.format("Peer %d has the preferred neighbors %s.", selfPeerId, sb.toString()));
    }

    public void logOptimisticallyUnchoked(int peerId) {
        logRaw(String.format("Peer %d has the optimistically unchoked neighbor %d.", selfPeerId, peerId));
    }

    public void logUnchoked(int byPeerId) {
        logRaw(String.format("Peer %d is unchoked by %d.", selfPeerId, byPeerId));
    }

    public void logChoked(int byPeerId) {
        logRaw(String.format("Peer %d is choked by %d.", selfPeerId, byPeerId));
    }

    public void logReceivedHave(int fromPeerId, int pieceIndex) {
        logRaw(String.format("Peer %d received the 'have' message from %d for the piece %d.",
                selfPeerId, fromPeerId, pieceIndex));
    }

    public void logReceivedInterested(int fromPeerId) {
        logRaw(String.format("Peer %d received the 'interested' message from %d.", selfPeerId, fromPeerId));
    }

    public void logReceivedNotInterested(int fromPeerId) {
        logRaw(String.format("Peer %d received the 'not interested' message from %d.", selfPeerId, fromPeerId));
    }

    public void logDownloaded(int fromPeerId, int pieceIndex, int totalPieces) {
        logRaw(String.format("Peer %d has downloaded the piece %d from %d. Now the number of pieces it has is %d.",
                selfPeerId, pieceIndex, fromPeerId, totalPieces));
    }

    public void logCompleted() {
        logRaw(String.format("Peer %d has downloaded the complete file.", selfPeerId));
    }

    @Override
    public void close() throws IOException {
        synchronized (writer) {
            writer.close();
        }
    }
}