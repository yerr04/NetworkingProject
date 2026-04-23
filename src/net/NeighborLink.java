package net;

import logging.PeerLogger;
import model.Bitfield;
import model.SwarmContext;
import util.ByteIO;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class NeighborLink {
    private static final byte[] POISON_PILL = new byte[0];

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SwarmContext state;
    private final PeerLogger logger;
    private final int remotePeerId;
    private final BlockingQueue<byte[]> outbound = new LinkedBlockingQueue<>();
    private final Thread reader;
    private final Thread writer;
    private final Random random = new Random();
    private volatile boolean closed = false;

    private NeighborLink(Socket socket, SwarmContext state, PeerLogger logger, int remotePeerId) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
        this.state = state;
        this.logger = logger;
        this.remotePeerId = remotePeerId;
        this.reader = new Thread(this::readLoop, "Reader-" + state.selfPeerId + "->" + remotePeerId);
        this.writer = new Thread(this::writeLoop, "Writer-" + state.selfPeerId + "->" + remotePeerId);
    }

    public int remotePeerId() {
        return remotePeerId;
    }

    public static NeighborLink connectOutgoing(String host, int port, int expectedPeerId,
                                               SwarmContext state, PeerLogger logger) throws IOException {
        Socket s = new Socket(host, port);
        InputStream rawIn = s.getInputStream();
        OutputStream rawOut = s.getOutputStream();
        Frame.writeHandshake(rawOut, state.selfPeerId);
        int remoteId = Frame.readHandshake(rawIn);
        if (remoteId != expectedPeerId) {
            s.close();
            throw new IOException("Expected peer " + expectedPeerId + " but handshake carried " + remoteId);
        }
        NeighborLink link = new NeighborLink(s, state, logger, remoteId);
        logger.logMakesConnectionTo(remoteId);
        link.registerAndStart();
        return link;
    }

    public static NeighborLink acceptIncoming(Socket s, SwarmContext state, PeerLogger logger) throws IOException {
        InputStream rawIn = s.getInputStream();
        OutputStream rawOut = s.getOutputStream();
        int remoteId = Frame.readHandshake(rawIn);
        Frame.writeHandshake(rawOut, state.selfPeerId);
        NeighborLink link = new NeighborLink(s, state, logger, remoteId);
        logger.logIsConnectedFrom(remoteId);
        link.registerAndStart();
        return link;
    }

    private void registerAndStart() {
        state.links.put(remotePeerId, this);
        state.peerBitfields.putIfAbsent(remotePeerId, new Bitfield(state.commonState.numPieces()));
        state.downloadTally.putIfAbsent(remotePeerId, new AtomicLong(0));
        state.requestsInFlightTo.putIfAbsent(remotePeerId, ConcurrentHashMap.newKeySet());
        state.lastInterestSignal.putIfAbsent(remotePeerId, false);

        if (state.myBitfield.count() > 0) {
            enqueue(Frame.BITFIELD, state.myBitfield.toBytes());
        }

        writer.start();
        reader.start();
    }

    private void readLoop() {
        try {
            while (!socket.isClosed() && !state.stopping) {
                Frame f = Frame.readFrame(in);
                handleFrame(f);
            }
        } catch (IOException e) {
        } finally {
            disconnect();
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                byte[] framed = outbound.take();
                if (framed == POISON_PILL) break;
                out.write(framed);
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            disconnect();
        }
    }

    private void handleFrame(Frame f) throws IOException {
        switch (f.type) {
            case Frame.CHOKE: handleChoke(); break;
            case Frame.UNCHOKE: handleUnchoke(); break;
            case Frame.INTERESTED: handleInterested(); break;
            case Frame.NOT_INTERESTED: handleNotInterested(); break;
            case Frame.HAVE: handleHave(f.payload); break;
            case Frame.BITFIELD: handleBitfield(f.payload); break;
            case Frame.REQUEST: handleRequest(f.payload); break;
            case Frame.PIECE: handlePiece(f.payload); break;
            default:
        }
    }

    private void handleChoke() {
        logger.logChoked(remotePeerId);
        state.downloadingFrom.remove(remotePeerId);
        Set<Integer> pending = state.requestsInFlightTo.get(remotePeerId);
        if (pending != null) {
            for (Integer idx : pending) state.piecesInFlight.remove(idx);
            pending.clear();
        }
    }

    private void handleUnchoke() {
        logger.logUnchoked(remotePeerId);
        state.downloadingFrom.add(remotePeerId);
        requestNextPiece();
    }

    private void handleInterested() {
        logger.logReceivedInterested(remotePeerId);
        state.interestedPeers.add(remotePeerId);
    }

    private void handleNotInterested() {
        logger.logReceivedNotInterested(remotePeerId);
        state.interestedPeers.remove(remotePeerId);
    }

    private void handleHave(byte[] payload) {
        int idx = ByteIO.bytesToInt(payload, 0);
        logger.logReceivedHave(remotePeerId, idx);
        Bitfield bf = state.peerBitfields.get(remotePeerId);
        if (bf != null) bf.set(idx);
        refreshInterestSignal();
    }

    private void handleBitfield(byte[] payload) {
        Bitfield bf = state.peerBitfields.get(remotePeerId);
        if (bf == null) {
            bf = new Bitfield(state.commonState.numPieces());
            state.peerBitfields.put(remotePeerId, bf);
        }
        bf.fromBytes(payload);
        refreshInterestSignal();
    }

    private void handleRequest(byte[] payload) throws IOException {
        int idx = ByteIO.bytesToInt(payload, 0);
        if (!state.uploadingTo.contains(remotePeerId)) return;
        if (!state.myBitfield.has(idx)) return;
        byte[] content = state.vault.loadPiece(idx);
        sendPiece(idx, content);
    }

    private void handlePiece(byte[] payload) throws IOException {
        int idx = ByteIO.bytesToInt(payload, 0);
        byte[] content = new byte[payload.length - 4];
        System.arraycopy(payload, 4, content, 0, content.length);

        AtomicLong counter = state.downloadTally.get(remotePeerId);
        if (counter != null) counter.addAndGet(content.length);

        Set<Integer> pending = state.requestsInFlightTo.get(remotePeerId);
        if (pending != null) pending.remove(idx);
        state.piecesInFlight.remove(idx);

        boolean wasNew = !state.myBitfield.has(idx);
        if (wasNew) {
            state.vault.storePiece(idx, content);
            state.myBitfield.set(idx);
            logger.logDownloaded(remotePeerId, idx, state.myBitfield.count());

            for (NeighborLink link : state.links.values()) {
                link.sendHave(idx);
            }
            for (NeighborLink link : state.links.values()) {
                link.refreshInterestSignal();
            }

            if (state.myBitfield.isComplete()) {
                logger.logCompleted();
            }
        }

        if (state.downloadingFrom.contains(remotePeerId) && !state.myBitfield.isComplete()) {
            requestNextPiece();
        }
    }

    public void refreshInterestSignal() {
        Bitfield bf = state.peerBitfields.get(remotePeerId);
        if (bf == null) return;
        boolean interesting = false;
        int n = state.commonState.numPieces();
        for (int i = 0; i < n; i++) {
            if (bf.has(i) && !state.myBitfield.has(i)) {
                interesting = true;
                break;
            }
        }
        Boolean last = state.lastInterestSignal.get(remotePeerId);
        if (last == null || last != interesting) {
            if (interesting) sendInterested();
            else sendNotInterested();
            state.lastInterestSignal.put(remotePeerId, interesting);
        }
    }

    private void requestNextPiece() {
        if (!state.downloadingFrom.contains(remotePeerId)) return;
        if (state.myBitfield.isComplete()) return;
        Bitfield bf = state.peerBitfields.get(remotePeerId);
        if (bf == null) return;

        int n = state.commonState.numPieces();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (bf.has(i) && !state.myBitfield.has(i) && !state.piecesInFlight.contains(i)) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) return;

        int pieceIdx = candidates.get(random.nextInt(candidates.size()));
        if (!state.piecesInFlight.add(pieceIdx)) return;
        Set<Integer> pendingFor = state.requestsInFlightTo.get(remotePeerId);
        if (pendingFor != null) pendingFor.add(pieceIdx);
        sendRequest(pieceIdx);
    }

    public void sendChoke()         { enqueue(Frame.CHOKE, new byte[0]); }
    public void sendUnchoke()       { enqueue(Frame.UNCHOKE, new byte[0]); }
    public void sendInterested()    { enqueue(Frame.INTERESTED, new byte[0]); }
    public void sendNotInterested() { enqueue(Frame.NOT_INTERESTED, new byte[0]); }
    public void sendHave(int idx)   { enqueue(Frame.HAVE, ByteIO.intToBytes(idx)); }
    public void sendRequest(int idx){ enqueue(Frame.REQUEST, ByteIO.intToBytes(idx)); }

    public void sendPiece(int idx, byte[] content) {
        byte[] payload = new byte[4 + content.length];
        byte[] idxBytes = ByteIO.intToBytes(idx);
        System.arraycopy(idxBytes, 0, payload, 0, 4);
        System.arraycopy(content, 0, payload, 4, content.length);
        enqueue(Frame.PIECE, payload);
    }

    private void enqueue(byte type, byte[] payload) {
        if (closed) return;
        byte[] framed = Frame.buildFrame(type, payload);
        outbound.offer(framed);
    }

    private void disconnect() {
        if (closed) return;
        closed = true;
        state.links.remove(remotePeerId, this);
        state.uploadingTo.remove(remotePeerId);
        state.downloadingFrom.remove(remotePeerId);
        state.interestedPeers.remove(remotePeerId);
        state.preferredNeighbors.remove(remotePeerId);
        if (Objects.equals(state.optimisticUnchokedNeighbor, remotePeerId)) {
            state.optimisticUnchokedNeighbor = null;
        }
        Set<Integer> pending = state.requestsInFlightTo.remove(remotePeerId);
        if (pending != null) {
            for (Integer idx : pending) state.piecesInFlight.remove(idx);
        }
        state.lastInterestSignal.remove(remotePeerId);
        outbound.offer(POISON_PILL);
        try { socket.close(); } catch (IOException ignore) {}
    }

    public void closeSocket() {
        disconnect();
    }
}