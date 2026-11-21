package org.nagineco.doppel.social;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Standalone relationship state engine. Not yet wired to Fabric NPC events.
 * Relationship state is individual; this does not create a second personality.
 */
public final class NaoRelationshipEngine {
    public enum Interaction { TALK, HELP, GIFT, ARGUE, APOLOGIZE, ROMANCE_REQUEST }
    public record Relationship(int familiarity, int trust, int affection, boolean romanceOptIn) {
        public Relationship {
            familiarity = clamp(familiarity);
            trust = clamp(trust);
            affection = clamp(affection);
        }
    }
    private record Pair(UUID from, UUID to) {}
    private final Map<Pair, Relationship> relationships = new HashMap<>();

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    public Relationship get(UUID from, UUID to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (from.equals(to)) throw new IllegalArgumentException("Self relationship");
        return relationships.getOrDefault(new Pair(from, to), new Relationship(0, 50, 0, false));
    }
    public void setRomanceOptIn(UUID from, UUID to, boolean enabled) {
        Relationship r = get(from, to);
        relationships.put(new Pair(from, to),
                new Relationship(r.familiarity(), r.trust(), r.affection(), enabled));
    }
    /** Interaction changes only the actor's directed view. */
    public Relationship apply(UUID from, UUID to, Interaction event) {
        Objects.requireNonNull(event);
        Relationship r = get(from, to);
        int f = r.familiarity(), t = r.trust(), a = r.affection();
        switch (event) {
            case TALK -> f += 2;
            case HELP -> { f += 1; t += 4; }
            case GIFT -> { f += 2; t += 1; }
            case ARGUE -> { t -= 6; a -= 3; }
            case APOLOGIZE -> t += 2;
            case ROMANCE_REQUEST -> {
                // Romance can advance only if both sides opted in.
                if (!r.romanceOptIn() || !get(to, from).romanceOptIn()) return r;
                f += 1; a += 2;
            }
        }
        Relationship next = new Relationship(f, t, a, r.romanceOptIn());
        relationships.put(new Pair(from, to), next);
        return next;
    }
    /** Snapshot by stable logical IDs for an external persistence adapter. */
    public Map<String, Relationship> snapshot() {
        Map<String, Relationship> result = new HashMap<>();
        relationships.forEach((k, v) -> result.put(k.from() + ":" + k.to(), v));
        return Map.copyOf(result);
    }
    public void restore(Map<String, Relationship> saved) {
        Objects.requireNonNull(saved);
        Map<Pair, Relationship> validated = new HashMap<>();
        for (var entry : saved.entrySet()) {
            String[] ids = entry.getKey().split(":", -1);
            if (ids.length != 2) throw new IllegalArgumentException("Invalid pair key");
            UUID from = UUID.fromString(ids[0]), to = UUID.fromString(ids[1]);
            if (from.equals(to)) throw new IllegalArgumentException("Self relationship");
            Relationship r = Objects.requireNonNull(entry.getValue());
            validated.put(new Pair(from, to), r);
        }
        relationships.clear();
        relationships.putAll(validated);
    }
}
