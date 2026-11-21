package org.nagineco.doppel;

import java.util.*;

/**
 * Bounded rollback plan. This is a journal contract, NOT a Minecraft world
 * snapshot or restore implementation. No block edits occur in this class.
 */
public final class NaoWorldRollbackPlan {
    public enum Scope { CLOCK_AND_OWNER, BOUNDED_REGION, FULL_WORLD }
    public record Policy(Scope scope, boolean explicitOptIn, int maxChunks) {
        public Policy {
            Objects.requireNonNull(scope);
            if (maxChunks < 0 || maxChunks > 4096) throw new IllegalArgumentException("chunk cap");
            if (scope != Scope.CLOCK_AND_OWNER && !explicitOptIn)
                throw new IllegalArgumentException("world rollback must be opt-in");
            if (scope == Scope.BOUNDED_REGION && maxChunks == 0)
                throw new IllegalArgumentException("bounded rollback needs chunk budget");
            if (scope == Scope.FULL_WORLD)
                throw new UnsupportedOperationException("full-world snapshot/restore is not implemented");
        }
    }
    public record BlockChange(String dimension, int x, int y, int z, String before, String after) {
        public BlockChange {
            if (dimension == null || dimension.isBlank() || before == null || after == null)
                throw new IllegalArgumentException("invalid block journal");
        }
    }
    private Policy policy = new Policy(Scope.CLOCK_AND_OWNER, false, 0);
    private final List<BlockChange> changes = new ArrayList<>();
    public synchronized Policy policy() { return policy; }
    public synchronized void configure(Policy value) {
        Objects.requireNonNull(value);
        if (!changes.isEmpty()) throw new IllegalStateException("journal must be cleared before reconfiguration");
        policy = value;
    }
    public synchronized void record(BlockChange change) {
        Objects.requireNonNull(change);
        if (policy.scope() == Scope.CLOCK_AND_OWNER) return;
        if (changes.size() >= policy.maxChunks() * 4096L)
            throw new IllegalStateException("journal budget exceeded");
        changes.add(change);
    }
    public synchronized List<BlockChange> reverseJournal() {
        List<BlockChange> copy = new ArrayList<>(changes);
        Collections.reverse(copy);
        return List.copyOf(copy);
    }
    public synchronized void clear() { changes.clear(); }
}
