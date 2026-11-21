package org.nagineco.doppel;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NaoCoreModelsTest {
    @Test void learnsFromSuccessesAndRejectsBadSnapshots() {
        NaoActionLearning learning = new NaoActionLearning();
        learning.observe(NaoActionLearning.Action.MINE, true);
        learning.observe(NaoActionLearning.Action.BUILD, false);
        assertTrue(learning.confidence(NaoActionLearning.Action.MINE) >
                   learning.confidence(NaoActionLearning.Action.BUILD));
        assertThrows(IllegalArgumentException.class,
            () -> learning.restore(Map.of(NaoActionLearning.Action.MINE, 1),
                Map.of(NaoActionLearning.Action.MINE, 2)));
    }
    @Test void ownerControlAndNpcSelectionAreDistinct() {
        NaoControlSession session = new NaoControlSession();
        assertEquals(NaoControlSession.Mode.OWNER, session.selection().mode());
        UUID id = UUID.randomUUID();
        assertEquals(id, session.select(256, id).logicalId());
        session.release();
        assertEquals(0, session.selection().slot());
        assertThrows(IllegalArgumentException.class, () -> session.select(257, id));
    }
    @Test void fullWorldRollbackCannotBeEnabledPrematurely() {
        assertThrows(UnsupportedOperationException.class,
            () -> new NaoWorldRollbackPlan.Policy(NaoWorldRollbackPlan.Scope.FULL_WORLD, true, 4096));
        assertThrows(IllegalArgumentException.class,
            () -> new NaoWorldRollbackPlan.Policy(NaoWorldRollbackPlan.Scope.BOUNDED_REGION, false, 2));
    }
    @Test void personalityProposalsNeedEvidence() {
        NaoPersonalityGrowth growth = new NaoPersonalityGrowth();
        for (int i = 0; i < 9; i++) growth.observe("collect", true);
        assertTrue(growth.suggest("collect").isEmpty());
        growth.observe("collect", true);
        assertEquals(10, growth.suggest("collect").orElseThrow().observations());
    }
}
