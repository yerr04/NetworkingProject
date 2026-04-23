package scheduler;

import logging.PeerLogger;
import model.SwarmContext;
import net.NeighborLink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class PreferredUnchokeTask implements Runnable {
    private final SwarmContext state;
    private final PeerLogger logger;
    private final Random random = new Random();

    public PreferredUnchokeTask(SwarmContext state, PeerLogger logger) {
        this.state = state;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            choose();
        } catch (Exception e) {
        }
    }

    private void choose() {
        int k = state.common.numberOfPreferredNeighbors;

        List<Integer> interested = new ArrayList<>();
        for (Integer p : state.interestedPeers) {
            if (state.links.containsKey(p)) interested.add(p);
        }

        List<Integer> newPreferredList;
        if (state.iAmComplete()) {
            Collections.shuffle(interested, random);
            newPreferredList = new ArrayList<>(interested.subList(0, Math.min(k, interested.size())));
        } else {
            Collections.shuffle(interested, random);
            interested.sort((a, b) -> {
                long va = tallyFor(a);
                long vb = tallyFor(b);
                return Long.compare(vb, va);
            });
            newPreferredList = new ArrayList<>(interested.subList(0, Math.min(k, interested.size())));
        }

        Set<Integer> newPreferred = new HashSet<>(newPreferredList);
        Set<Integer> oldPreferred = new HashSet<>(state.preferredNeighbors);

        for (Integer p : newPreferred) {
            if (!state.uploadingTo.contains(p)) {
                NeighborLink link = state.links.get(p);
                if (link != null) link.sendUnchoke();
                state.uploadingTo.add(p);
            }
        }

        Integer opt = state.optimisticUnchokedNeighbor;
        for (Integer p : oldPreferred) {
            if (!newPreferred.contains(p) && !Objects.equals(opt, p)) {
                NeighborLink link = state.links.get(p);
                if (link != null) link.sendChoke();
                state.uploadingTo.remove(p);
            }
        }

        state.preferredNeighbors.clear();
        state.preferredNeighbors.addAll(newPreferred);

        for (AtomicLong c : state.downloadTally.values()) c.set(0);

        logger.logPreferredNeighbors(newPreferredList);
    }

    private long tallyFor(int peerId) {
        AtomicLong c = state.downloadTally.get(peerId);
        return c == null ? 0L : c.get();
    }
}