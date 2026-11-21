package org.nagineco.doppel;

import java.util.*;

/**
 * Evidence-backed shared personality growth suggestions. Never edits
 * nao_personality.md without explicit owner approval.
 */
public final class NaoPersonalityGrowth {
    public record Proposal(String trait, int proposedStrength, int observations, double confidence) {
        public Proposal {
            if (trait == null || !trait.matches("[a-z_]{1,32}") || proposedStrength < 0
                || proposedStrength > 10 || observations < 1 || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("invalid proposal");
        }
    }
    private final Map<String, Integer> positive = new HashMap<>();
    private final Map<String, Integer> negative = new HashMap<>();
    public void observe(String trait, boolean success) {
        if (trait == null || !trait.matches("[a-z_]{1,32}"))
            throw new IllegalArgumentException("invalid trait");
        Map<String,Integer> target = success ? positive : negative;
        target.merge(trait, 1, (a,b) -> Math.min(100000, a + b));
    }
    public Optional<Proposal> suggest(String trait) {
        int good = positive.getOrDefault(trait,0), bad = negative.getOrDefault(trait,0);
        int count = good + bad;
        if (count < 10) return Optional.empty();
        double probability = (good + 1.0) / (count + 2.0);
        int strength = Math.max(0, Math.min(10, (int)Math.round(probability * 10)));
        return Optional.of(new Proposal(trait, strength, count, probability));
    }
    public List<Proposal> suggestions() {
        Set<String> keys = new TreeSet<>(positive.keySet());
        keys.addAll(negative.keySet());
        return keys.stream().map(this::suggest).flatMap(Optional::stream).toList();
    }
}
