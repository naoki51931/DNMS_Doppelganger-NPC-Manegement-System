package org.nagineco.doppel;

import java.util.*;

/**
 * Safe state machine for future possession. Does not intercept player packets or
 * spawn ServerPlayerEntity; real possession must use a Fabric server adapter.
 */
public final class NaoControlSession {
    public enum Mode { OWNER, NPC }
    public record Selection(Mode mode, int slot, UUID logicalId) {
        public Selection {
            Objects.requireNonNull(mode);
            if (mode == Mode.NPC && (slot < 1 || slot > 24 || logicalId == null))
                throw new IllegalArgumentException("invalid NPC selection");
            if (mode == Mode.OWNER && (slot != 0 || logicalId != null))
                throw new IllegalArgumentException("invalid owner selection");
        }
    }
    private Selection selection = new Selection(Mode.OWNER, 0, null);
    public synchronized Selection selection() { return selection; }
    public synchronized Selection select(int slot, UUID logicalId) {
        selection = new Selection(Mode.NPC, slot, logicalId);
        return selection;
    }
    public synchronized void release() { selection = new Selection(Mode.OWNER, 0, null); }
    public synchronized void restore(Selection value) { selection = Objects.requireNonNull(value); }
}
