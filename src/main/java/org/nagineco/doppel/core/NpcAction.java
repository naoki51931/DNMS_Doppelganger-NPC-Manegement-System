package org.nagineco.doppel.core;

/** Immutable game-independent action request produced by an NPC decision. */
public record NpcAction(Kind kind) {
    public enum Kind { STAY, NORTH, SOUTH, WEST, EAST }
    public NpcAction {
        if (kind == null) throw new IllegalArgumentException("Action kind is required");
    }
}
