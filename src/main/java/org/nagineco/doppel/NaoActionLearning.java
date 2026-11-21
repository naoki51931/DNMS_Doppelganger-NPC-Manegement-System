package org.nagineco.doppel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Evidence-based, bounded, per-Nao learned action tendencies.
 * Does NOT rewrite the authoritative shared personality file.
 */
public final class NaoActionLearning {
    public enum Action { MINE, BUILD, COLLECT, TALK, HELP, EXPLORE }
    private final EnumMap<Action, Integer> attempts = new EnumMap<>(Action.class);
    private final EnumMap<Action, Integer> successes = new EnumMap<>(Action.class);

    public void observe(Action action, boolean succeeded) {
        if (action == null) throw new IllegalArgumentException("action");
        attempts.merge(action, 1, (a,b) -> Math.min(100000, a + b));
        if (succeeded) successes.merge(action, 1, (a,b) -> Math.min(100000, a + b));
    }

    /** Bayesian-smoothed success rate, 0..1, never a claim of semantic understanding. */
    public double confidence(Action action) {
        return (successes.getOrDefault(action, 0) + 1.0)
            / (attempts.getOrDefault(action, 0) + 2.0);
    }
    public Action choose(Action[] available) {
        if (available == null || available.length == 0) throw new IllegalArgumentException("available");
        Action best = null;
        double score = -1;
        for (Action action : available) {
            if (action == null) throw new IllegalArgumentException("null action");
            double candidate = confidence(action);
            if (candidate > score) { score = candidate; best = action; }
        }
        return best;
    }
    public Map<Action, Integer> attempts() { return Map.copyOf(attempts); }
    public Map<Action, Integer> successes() { return Map.copyOf(successes); }
    public void restore(Map<Action,Integer> attempted, Map<Action,Integer> successful) {
        if (attempted == null || successful == null) throw new IllegalArgumentException("null snapshot");
        for (Action action : Action.values()) {
            int a = attempted.getOrDefault(action,0), s = successful.getOrDefault(action,0);
            if (a < 0 || a > 100000 || s < 0 || s > a) throw new IllegalArgumentException("invalid counts");
        }
        attempts.clear(); attempts.putAll(attempted);
        successes.clear(); successes.putAll(successful);
    }
}
