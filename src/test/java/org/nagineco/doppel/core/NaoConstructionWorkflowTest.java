package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NaoConstructionWorkflowTest {
    @Test void missingLogsRequiresGathering() {
        assertEquals(NaoConstructionWorkflow.Phase.GATHER,
            NaoConstructionWorkflow.next(true, 2, 0, true));
    }
    @Test void collectedLogsRequireReturnToSite() {
        assertEquals(NaoConstructionWorkflow.Phase.RETURN,
            NaoConstructionWorkflow.next(true, 2, 1, false));
    }
    @Test void returnEnablesResumingSavedStep() {
        assertEquals(NaoConstructionWorkflow.Phase.BUILD,
            NaoConstructionWorkflow.next(true, 2, 1, true));
        assertEquals(new NaoBuildingPlan.Offset(-1, 0, 1), NaoBuildingPlan.next(2));
    }
    @Test void finishedPlanDoesNotCollectMoreLogs() {
        assertEquals(NaoConstructionWorkflow.Phase.COMPLETE,
            NaoConstructionWorkflow.next(true, 8, 0, false));
    }
    @Test void inactiveTaskDoesNotOverrideOtherGoals() {
        assertEquals(NaoConstructionWorkflow.Phase.IDLE,
            NaoConstructionWorkflow.next(false, 0, 0, false));
    }
    @Test void nearSiteUsesBoundedHorizontalDistance() {
        assertTrue(NaoConstructionWorkflow.near(1, 1, 2, 2));
        assertFalse(NaoConstructionWorkflow.near(1, 1, 10, 10));
    }
}
