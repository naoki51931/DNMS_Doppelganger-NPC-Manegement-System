package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NaoRouteScheduleTest {
    @Test void everyNpcGetsOneScheduledRefreshPerTwentyTicks() {
        for (int slot = 0; slot < 256; slot++) {
            int refreshes = 0;
            for (int tick = 0; tick < 20; tick++)
                if (NaoRouteSchedule.shouldRefresh(slot, tick, false, false)) refreshes++;
            assertEquals(1, refreshes);
        }
    }
    @Test void perTickRefreshLoadIsBoundedFor256Npcs() {
        for (int tick = 0; tick < 20; tick++) {
            int refreshes = 0;
            for (int slot = 0; slot < 256; slot++)
                if (NaoRouteSchedule.shouldRefresh(slot, tick, false, false)) refreshes++;
            assertTrue(refreshes <= 13);
        }
    }
    @Test void missingOrChangedWaypointTriggersImmediateReplan() {
        assertTrue(NaoRouteSchedule.shouldRefresh(1, 0, true, false));
        assertTrue(NaoRouteSchedule.shouldRefresh(1, 0, false, true));
    }
    @Test void invalidSlotIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> NaoRouteSchedule.shouldRefresh(256, 0, false, false));
    }
}
