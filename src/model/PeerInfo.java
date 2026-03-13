package model;

public final class PeerInfo {
    private final int peerId;
    private final String host;
    private final int port;
    private final boolean hasFile;

    public PeerInfo(int peerId, String host, int port, boolean hasFile) {
        if (peerId <= 0) throw new IllegalArgumentException("peerId must be positive");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host must be non-empty");
        if (port <= 0 || port > 65535) throw new IllegalArgumentException("port out of range");
        this.peerId = peerId;
        this.host = host;
        this.port = port;
        this.hasFile = hasFile;
    }

    public int peerId() {
        return peerId;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean hasFile() {
        return hasFile;
    }
}
