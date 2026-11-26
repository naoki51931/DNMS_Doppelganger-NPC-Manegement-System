package org.nagineco.doppel;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class NpcSpawnGuard {
    private NpcSpawnGuard() {}
    static <T> void register(List<T> bodies, int slot, T body,
                             BooleanSupplier spawn, Consumer<String> error) {
        if (!spawn.getAsBoolean()) {
            String message = "NPC body spawn failed for Doppel " + (slot + 1);
            error.accept(message);
            throw new IllegalStateException(message);
        }
        if (slot < bodies.size()) bodies.set(slot, body);
        else bodies.add(body);
    }
}
