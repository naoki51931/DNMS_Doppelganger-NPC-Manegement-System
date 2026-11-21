package org.nagineco.doppel.appearance;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NaoAppearanceRegistryTest {
    @Test void supportsTwentyFourStableIdentities() {
        NaoAppearanceRegistry registry = new NaoAppearanceRegistry();
        for (int slot = 1; slot <= 24; slot++)
            registry.ensure(slot, UUID.randomUUID(), "nao/default");
        assertEquals(24, registry.size());
        assertEquals(24, registry.snapshot().size());
        assertEquals(1, registry.snapshot().get(0).slot());
        assertEquals(24, registry.snapshot().get(23).slot());
    }

    @Test void rejectsDuplicateLogicalIdAndSlotReplacement() {
        NaoAppearanceRegistry registry = new NaoAppearanceRegistry();
        UUID id = UUID.randomUUID();
        registry.ensure(1, id, "nao/default");
        assertThrows(IllegalArgumentException.class, () -> registry.ensure(2, id, "nao/default"));
        assertThrows(IllegalStateException.class, () -> registry.ensure(1, UUID.randomUUID(), "nao/default"));
    }

    @Test void rejectsUnsafeSkinKeysAndOutOfRangeSlots() {
        assertThrows(IllegalArgumentException.class,
            () -> new NaoAppearanceRegistry.NaoAppearance(1, UUID.randomUUID(), "../invalid", true));
        assertThrows(IllegalArgumentException.class,
            () -> new NaoAppearanceRegistry.NaoAppearance(257, UUID.randomUUID(), "nao/default", true));
    }

    @Test void restoreIsAtomicOnDuplicate() {
        NaoAppearanceRegistry registry = new NaoAppearanceRegistry();
        UUID id = UUID.randomUUID();
        registry.ensure(1, id, "nao/default");
        List<NaoAppearanceRegistry.NaoAppearance> invalid = List.of(
            new NaoAppearanceRegistry.NaoAppearance(2, UUID.randomUUID(), "nao/blue", true),
            new NaoAppearanceRegistry.NaoAppearance(2, UUID.randomUUID(), "nao/red", true));
        assertThrows(IllegalArgumentException.class, () -> registry.restore(invalid));
        assertEquals(1, registry.size());
        assertEquals(id, registry.get(1).logicalId());
    }

    @Test void snapshotCanRestoreAppearanceChanges() {
        NaoAppearanceRegistry original = new NaoAppearanceRegistry();
        UUID id = UUID.randomUUID();
        original.ensure(1, id, "nao/default");
        original.setSkin(1, "nao/blue");
        original.setActive(1, false);
        NaoAppearanceRegistry copy = new NaoAppearanceRegistry();
        copy.restore(original.snapshot());
        assertEquals(id, copy.get(1).logicalId());
        assertEquals("nao/blue", copy.get(1).skinKey());
        assertFalse(copy.get(1).active());
    }
}
