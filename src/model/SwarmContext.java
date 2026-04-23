package model;

import config.CommonConfig;
import config.PeerInfoConfig;
import io.PieceVault;
import net.NeighborLink;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SwarmContext {
    public final int selfPeerId;
    public final CommonConfig common;
    public final CommonState commonState;
    public final PeerInfoConfig peers;
    public final PieceVault vault;
    public final Bitfield myBitfield;

    public final ConcurrentHashMap<Integer, NeighborLink> links = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, Bitfield> peerBitfields = new ConcurrentHashMap<>();
    public final Set<Integer> interestedPeers = ConcurrentHashMap.newKeySet();
    public final Set<Integer> uploadingTo = ConcurrentHashMap.newKeySet();
    public final Set<Integer> downloadingFrom = ConcurrentHashMap.newKeySet();
    public final Set<Integer> preferredNeighbors = ConcurrentHashMap.newKeySet();
    public final ConcurrentHashMap<Integer, AtomicLong> downloadTally = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, Set<Integer>> requestsInFlightTo = new ConcurrentHashMap<>();
    public final Set<Integer> piecesInFlight = ConcurrentHashMap.newKeySet();
    public final ConcurrentHashMap<Integer, Boolean> lastInterestSignal = new ConcurrentHashMap<>();

    public volatile Integer optimisticUnchokedNeighbor = null;
    public volatile boolean stopping = false;

    public SwarmContext(int selfPeerId,
                        CommonConfig common,
                        CommonState commonState,
                        PeerInfoConfig peers,
                        PieceVault vault,
                        Bitfield myBitfield) {
        this.selfPeerId = selfPeerId;
        this.common = common;
        this.commonState = commonState;
        this.peers = peers;
        this.vault = vault;
        this.myBitfield = myBitfield;
    }

    public boolean iAmComplete() {
        return myBitfield.isComplete();
    }

    public boolean allPeersComplete() {
        if (!myBitfield.isComplete()) return false;
        for (PeerInfo p : peers.peersInOrder()) {
            if (p.peerId() == selfPeerId) continue;
            Bitfield bf = peerBitfields.get(p.peerId());
            if (bf == null || !bf.isComplete()) return false;
        }
        return true;
    }
}