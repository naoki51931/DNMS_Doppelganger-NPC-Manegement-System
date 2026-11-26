package org.nagineco.doppel;

import java.util.HashMap;
import org.nagineco.doppel.core.RewardEstimate;

/** Goal-level reward extension of the existing Reward Learning Patch. */
public final class NaoRlpPatch {
    private NaoRlpPatch() {}
    public static void record(NaoLifeStore.Nao nao, NaoLifeStore.Goal goal, double reward) {
        if (!Double.isFinite(reward)) return;
        if (nao.goalRewards == null) nao.goalRewards = new HashMap<>();
        if (nao.goalAttempts == null) nao.goalAttempts = new HashMap<>();
        String key = goal.name();
        RewardEstimate estimate = new RewardEstimate();
        estimate.restore(nao.goalAttempts.getOrDefault(key, 0), nao.goalRewards.getOrDefault(key, 0.0));
        estimate.record(reward);
        nao.goalAttempts.put(key, estimate.count());
        nao.goalRewards.put(key, estimate.mean());
    }
    public static double preference(NaoLifeStore.Nao nao, NaoLifeStore.Goal goal) {
        if (nao.goalRewards == null || nao.goalAttempts == null) return 0;
        int count = nao.goalAttempts.getOrDefault(goal.name(), 0);
        if (count == 0) return 0;
        RewardEstimate estimate = new RewardEstimate();
        estimate.restore(count, nao.goalRewards.getOrDefault(goal.name(), 0.0));
        return estimate.preference();
    }
}
