package org.nagineco.doppel.appearance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stable, rendering-agnostic identity/appearance model for up to 24 Nao NPCs.
 * This is NOT a Minecraft player entity, skin packet sender, or renderer.
 * The caller is responsible for storing snapshot() in a persistent checkpoint.
 */
public final class NaoAppearanceRegistry {
    public static final int MAX_NAO = 256;
    public record NaoAppearance(int slot, UUID logicalId, String skinKey, boolean active) {
        public NaoAppearance {
            if (slot < 1 || slot > MAX_NAO) throw new IllegalArgumentException("slot must be 1..256");
            Objects.requireNonNull(logicalId, "logicalId");
            Objects.requireNonNull(skinKey, "skinKey");
            if (!skinKey.matches("[a-zA-Z0-9_./:-]{1,128}") || skinKey.contains(".."))
                throw new IllegalArgumentException("invalid skin key");
        }
    }

    private final Map<Integer, NaoAppearance> bySlot = new HashMap<>();

    public synchronized NaoAppearance ensure(int slot, UUID logicalId, String skinKey) {
        Objects.requireNonNull(logicalId);
        NaoAppearance existing = bySlot.get(slot);
        if (existing != null) {
            if (!existing.logicalId().equals(logicalId))
                throw new IllegalStateException("slot already belongs to a different identity");
            return existing;
        }
        if (bySlot.values().stream().anyMatch(e -> e.logicalId().equals(logicalId)))
            throw new IllegalArgumentException("duplicate logical ID");
        NaoAppearance result = new NaoAppearance(slot, logicalId, skinKey, true);
        bySlot.put(slot, result);
        return result;
    }

    public synchronized NaoAppearance get(int slot) { return bySlot.get(slot); }

    public synchronized void setActive(int slot, boolean active) {
        NaoAppearance current = Objects.requireNonNull(bySlot.get(slot), "unknown slot");
        bySlot.put(slot, new NaoAppearance(slot, current.logicalId(), current.skinKey(), active));
    }

    public synchronized void setSkin(int slot, String skinKey) {
        NaoAppearance current = Objects.requireNonNull(bySlot.get(slot), "unknown slot");
        bySlot.put(slot, new NaoAppearance(slot, current.logicalId(), skinKey, current.active()));
    }

    public synchronized List<NaoAppearance> snapshot() {
        return bySlot.values().stream().sorted((a, b) -> Integer.compare(a.slot(), b.slot())).toList();
    }

    /** Atomic validation and replacement; no partial state on invalid snapshots. */
    public synchronized void restore(List<NaoAppearance> entries) {
        Objects.requireNonNull(entries);
        if (entries.size() > MAX_NAO) throw new IllegalArgumentException("too many NPCs");
        Map<Integer, NaoAppearance> next = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (NaoAppearance entry : entries) {
            Objects.requireNonNull(entry);
            if (next.putIfAbsent(entry.slot(), entry) != null || !ids.add(entry.logicalId()))
                throw new IllegalArgumentException("duplicate slot or logical ID");
        }
        bySlot.clear();
        bySlot.putAll(next);
    }

    public synchronized int size() { return bySlot.size(); }
}
