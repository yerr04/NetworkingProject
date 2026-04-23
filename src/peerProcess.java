import config.CommonConfig;
import config.PeerInfoConfig;
import io.PieceVault;
import logging.PeerLogger;
import model.Bitfield;
import model.CommonState;
import model.PeerInfo;
import model.SwarmContext;
import net.NeighborLink;
import scheduler.OptimisticUnchokeTask;
import scheduler.PreferredUnchokeTask;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

        PieceVault vault = new PieceVault(commonState, peerDir, common.fileName);
        Bitfield myBitfield = new Bitfield(commonState.numPieces());
        if (self.hasFile()) {
            vault.loadFromDisk();
            myBitfield.setAll();
        }

        PeerLogger logger = new PeerLogger(peerId, workingDir);
        logger.logRaw("Peer " + peerId + " starting. Pieces: " + commonState.numPieces()
                + ", hasFile=" + self.hasFile());

        SwarmContext state = new SwarmContext(peerId, common, commonState, peers, vault, myBitfield);

        ServerSocket server = new ServerSocket(self.port());
        Thread acceptor = new Thread(() -> {
            while (!state.stopping) {
                try {
                    Socket s = server.accept();
                    NeighborLink.acceptIncoming(s, state, logger);
                } catch (IOException e) {
                    if (!state.stopping) {
                        System.err.println("accept error: " + e.getMessage());
                    }
                }
            }
        }, "Acceptor-" + peerId);
        acceptor.setDaemon(true);
        acceptor.start();

        int myIdx = peers.indexOfPeer(peerId);
        List<PeerInfo> all = peers.peersInOrder();
        for (int i = 0; i < myIdx; i++) {
            PeerInfo other = all.get(i);
            NeighborLink link = null;
            for (int attempt = 0; attempt < 30 && link == null; attempt++) {
                try {
                    link = NeighborLink.connectOutgoing(
                            other.host(), other.port(), other.peerId(), state, logger);
                } catch (IOException e) {
                    Thread.sleep(1000);
                }
            }
            if (link == null) {
                System.err.println("Failed to connect to peer " + other.peerId());
            }
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Scheduler-" + peerId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(new PreferredUnchokeTask(state, logger),
                common.unchokingIntervalSeconds,
                common.unchokingIntervalSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(new OptimisticUnchokeTask(state, logger),
                common.optimisticUnchokingIntervalSeconds,
                common.optimisticUnchokingIntervalSeconds, TimeUnit.SECONDS);

        while (!state.allPeersComplete()) {
            Thread.sleep(2000);
        }

        Thread.sleep(2000);
        state.stopping = true;
        scheduler.shutdownNow();
        try { server.close(); } catch (IOException ignore) {}
        for (NeighborLink link : state.links.values()) {
            link.closeSocket();
        }
        logger.close();
        System.exit(0);
    }
}