package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end portable construction-state regression: no Minecraft runtime required.
 * World block placement and entity movement are verified separately by Fabric integration.
 */
class NaoConstructionJourneyIntegrationTest {
    @Test void gatherReturnBuildAndFinishAllEightSteps() {
        int progress = 0;
        int logs = 0;
        boolean atSite = true;
        int harvested = 0;
        int placed = 0;
        while (progress < NaoBuildingPlan.starter().size()) {
            assertEquals(NaoConstructionWorkflow.Phase.GATHER,
                NaoConstructionWorkflow.next(true, progress, logs, atSite));
            logs++;
            harvested++;
            atSite = false;
            assertEquals(NaoConstructionWorkflow.Phase.RETURN,
                NaoConstructionWorkflow.next(true, progress, logs, atSite));
            var route = NaoGridPathfinder.find(
                new NaoGridPathfinder.Cell(4, 0),
                new NaoGridPathfinder.Cell(0, 0),
                (x, z) -> !(x == 2 && z == 0), 8);
            assertFalse(route.isEmpty());
            assertEquals(new NaoGridPathfinder.Cell(0, 0), route.get(route.size() - 1));
            atSite = true;
            assertEquals(NaoConstructionWorkflow.Phase.BUILD,
                NaoConstructionWorkflow.next(true, progress, logs, atSite));
            assertNotNull(NaoBuildingPlan.next(progress));
            progress++;
            logs--;
            placed++;
        }
        assertEquals(8, harvested);
        assertEquals(8, placed);
        assertEquals(0, logs);
        assertEquals(NaoConstructionWorkflow.Phase.COMPLETE,
            NaoConstructionWorkflow.next(true, progress, logs, false));
    }

    @Test void partialProgressCanResumeAfterRestoration() {
        int savedStep = 5;
        int savedLogs = 2;
        assertEquals(3, NaoBuildingPlan.remaining(savedStep));
        assertEquals(NaoConstructionWorkflow.Phase.RETURN,
            NaoConstructionWorkflow.next(true, savedStep, savedLogs, false));
        assertEquals(NaoConstructionWorkflow.Phase.BUILD,
            NaoConstructionWorkflow.next(true, savedStep, savedLogs, true));
        assertEquals(NaoBuildingPlan.starter().get(savedStep), NaoBuildingPlan.next(savedStep));
    }

    @Test void navigationLoadIsStaggeredAcrossAll256Slots() {
        int total = 0;
        for (int tick = 0; tick < 20; tick++) {
            int perTick = 0;
            for (int slot = 0; slot < 256; slot++) {
                if (NaoRouteSchedule.shouldRefresh(slot, tick, false, false)) perTick++;
            }
            assertTrue(perTick <= 13);
            total += perTick;
        }
        assertEquals(256, total);
    }

    @Test void blockedRouteDoesNotInventMovementOrResources() {
        List<NaoGridPathfinder.Cell> route = NaoGridPathfinder.find(
            new NaoGridPathfinder.Cell(0, 0),
            new NaoGridPathfinder.Cell(3, 0),
            (x, z) -> x == 0, 8);
        assertTrue(route.isEmpty());
        assertEquals(NaoConstructionWorkflow.Phase.GATHER,
            NaoConstructionWorkflow.next(true, 3, 0, false));
    }
}
