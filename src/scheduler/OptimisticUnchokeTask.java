package scheduler;

import logging.PeerLogger;
import model.SwarmContext;
import net.NeighborLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class OptimisticUnchokeTask implements Runnable {
    private final SwarmContext state;
    private final PeerLogger logger;
    private final Random random = new Random();

    public OptimisticUnchokeTask(SwarmContext state, PeerLogger logger) {
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
        List<Integer> candidates = new ArrayList<>();
        for (Integer p : state.interestedPeers) {
            if (!state.links.containsKey(p)) continue;
            if (state.uploadingTo.contains(p)) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return;

        int chosen = candidates.get(random.nextInt(candidates.size()));
        Integer old = state.optimisticUnchokedNeighbor;
        if (Objects.equals(old, chosen)) return;

        if (old != null && !state.preferredNeighbors.contains(old)) {
            NeighborLink oldLink = state.links.get(old);
            if (oldLink != null) oldLink.sendChoke();
            state.uploadingTo.remove(old);
        }

        state.optimisticUnchokedNeighbor = chosen;
        if (!state.uploadingTo.contains(chosen)) {
            NeighborLink newLink = state.links.get(chosen);
            if (newLink != null) newLink.sendUnchoke();
            state.uploadingTo.add(chosen);
        }
        logger.logOptimisticallyUnchoked(chosen);
    }
}