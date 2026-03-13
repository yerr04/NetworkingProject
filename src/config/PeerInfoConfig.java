package config;

import model.PeerInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PeerInfoConfig {
    private final List<PeerInfo> peersInOrder;

    private PeerInfoConfig(List<PeerInfo> peersInOrder) {
        this.peersInOrder = peersInOrder;
    }

    public List<PeerInfo> peersInOrder() {
        return peersInOrder;
    }

    public PeerInfo findPeer(int peerId) {
        for (PeerInfo p : peersInOrder) {
            if (p.peerId() == peerId) return p;
        }
        return null;
    }

    public int indexOfPeer(int peerId) {
        for (int i = 0; i < peersInOrder.size(); i++) {
            if (peersInOrder.get(i).peerId() == peerId) return i;
        }
        return -1;
    }

    public static PeerInfoConfig load(File peerInfoCfg) throws IOException {
        if (peerInfoCfg == null) throw new IllegalArgumentException("PeerInfo.cfg file is null");
        if (!peerInfoCfg.exists()) throw new IOException("Missing PeerInfo.cfg at: " + peerInfoCfg.getPath());

        List<PeerInfo> peers = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(peerInfoCfg))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                int peerId = parsePositiveInt(parts[0], "peerId");
                String host = parts[1];
                int port = parsePositiveInt(parts[2], "port");
                int hasFileInt = parseNonNegativeInt(parts[3], "hasFile");
                boolean hasFile = hasFileInt == 1;
                if (hasFileInt != 0 && hasFileInt != 1) {
                    throw new IllegalArgumentException("PeerInfo.cfg hasFile must be 0 or 1");
                }
                peers.add(new PeerInfo(peerId, host, port, hasFile));
            }
        }

        if (peers.isEmpty()) throw new IllegalArgumentException("PeerInfo.cfg had no valid peer rows");
        return new PeerInfoConfig(Collections.unmodifiableList(peers));
    }

    private static int parsePositiveInt(String s, String label) {
        try {
            int v = Integer.parseInt(s);
            if (v <= 0) throw new IllegalArgumentException(label + " must be positive");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
    }

    private static int parseNonNegativeInt(String s, String label) {
        try {
            int v = Integer.parseInt(s);
            if (v < 0) throw new IllegalArgumentException(label + " must be >= 0");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
    }
}
