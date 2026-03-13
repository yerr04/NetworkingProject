import config.CommonConfig;
import config.PeerInfoConfig;
import logging.PeerLogger;
import model.CommonState;
import model.PeerInfo;

import java.io.File;

public final class peerProcess {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java peerProcess <peerID>");
            System.exit(2);
        }

        int peerId;
        try {
            peerId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("peerID must be an integer");
            System.exit(2);
            return;
        }

        File workingDir = new File(System.getProperty("user.dir"));
        CommonConfig common = CommonConfig.load(new File(workingDir, "Common.cfg"));
        PeerInfoConfig peers = PeerInfoConfig.load(new File(workingDir, "PeerInfo.cfg"));
        PeerInfo self = peers.findPeer(peerId);
        if (self == null) {
            throw new IllegalArgumentException("peerID " + peerId + " not found in PeerInfo.cfg");
        }

        CommonState commonState = new CommonState(common.fileSize, common.pieceSize);

        File peerDir = new File(workingDir, "peer_" + peerId);
        if (!peerDir.exists() && !peerDir.mkdirs()) {
            throw new IllegalStateException("Failed to create peer directory: " + peerDir.getPath());
        }

        try (PeerLogger logger = new PeerLogger(peerId, workingDir)) {
            logger.logRaw("Peer " + peerId + " starting. Pieces: " + commonState.numPieces());
        }
    }
}
