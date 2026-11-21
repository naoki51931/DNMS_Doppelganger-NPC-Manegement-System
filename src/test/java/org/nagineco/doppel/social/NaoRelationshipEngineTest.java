package org.nagineco.doppel.social;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NaoRelationshipEngineTest {
    @Test void interactionsAreDirectedAndBounded() {
        NaoRelationshipEngine engine = new NaoRelationshipEngine();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        for (int i = 0; i < 100; i++) engine.apply(a, b, NaoRelationshipEngine.Interaction.HELP);
        assertEquals(100, engine.get(a, b).trust());
        assertEquals(50, engine.get(b, a).trust());
    }

    @Test void romanceRequiresMutualOptIn() {
        NaoRelationshipEngine engine = new NaoRelationshipEngine();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        engine.setRomanceOptIn(a, b, true);
        assertEquals(0, engine.apply(a, b, NaoRelationshipEngine.Interaction.ROMANCE_REQUEST).affection());
        engine.setRomanceOptIn(b, a, true);
        assertEquals(2, engine.apply(a, b, NaoRelationshipEngine.Interaction.ROMANCE_REQUEST).affection());
    }

    @Test void snapshotRestoresWithoutPartialMutation() {
        NaoRelationshipEngine engine = new NaoRelationshipEngine();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        engine.apply(a, b, NaoRelationshipEngine.Interaction.TALK);
        Map<String, NaoRelationshipEngine.Relationship> saved = engine.snapshot();
        NaoRelationshipEngine restored = new NaoRelationshipEngine();
        restored.restore(saved);
        assertEquals(engine.get(a, b), restored.get(a, b));
        assertThrows(IllegalArgumentException.class,
            () -> restored.restore(Map.of("not-a-pair", engine.get(a, b))));
        assertEquals(engine.get(a, b), restored.get(a, b));
    }
}
