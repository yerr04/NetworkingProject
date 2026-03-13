package logging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    @Override
    public void close() throws IOException {
        synchronized (writer) {
            writer.close();
        }
    }
}
