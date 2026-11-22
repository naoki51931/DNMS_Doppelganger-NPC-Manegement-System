package org.nagineco.doppel.core;

/**
 * Pure decision boundary. Learned movement is supplied by the caller; the
 * policy preserves REST/SAFETY pauses and the deterministic fallback.
 */
public final class NpcDecisionEngine {
    private NpcDecisionEngine() {}

    public static NpcAction decide(boolean restOrSafety, NpcAction learned,
                                   int tick, int slot) {
        if (restOrSafety) return new NpcAction(NpcAction.Kind.STAY);
        if (learned != null && learned.kind() != NpcAction.Kind.STAY) return learned;
        NpcAction.Kind[] directions = {
            NpcAction.Kind.NORTH, NpcAction.Kind.EAST,
            NpcAction.Kind.SOUTH, NpcAction.Kind.WEST
        };
        return new NpcAction(directions[Math.floorMod((tick / 80) + slot, directions.length)]);
    }
}
