package org.nagineco.doppel.core;

/** Deterministic staggering of expensive route planning for up to 256 NPCs. */
public final class NaoRouteSchedule {
    private NaoRouteSchedule() {}
    public static final int INTERVAL = 20;
    public static boolean shouldRefresh(int slot, long tick, boolean missing, boolean changed) {
        if (slot < 0 || slot >= 256) throw new IllegalArgumentException("slot");
        return missing || changed || Math.floorMod(tick + slot, INTERVAL) == 0;
    }
}
