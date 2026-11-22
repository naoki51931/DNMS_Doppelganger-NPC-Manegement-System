package org.nagineco.doppel.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialPartnerSelectorTest {
    @Test
    void selectsNearestActiveWithinSixBlocks() {
        boolean[] active = {true, true, true};
        double[] x = {0, 5, 2}, y = {0, 0, 0}, z = {0, 0, 0};
        assertEquals(2, SocialPartnerSelector.nearest(0, active, x, y, z, 36.0));
    }

    @Test
    void excludesRemovedAndOutOfRangeEntities() {
        boolean[] active = {true, false, true};
        double[] x = {0, 1, 6}, y = {0, 0, 0}, z = {0, 0, 0};
        assertEquals(-1, SocialPartnerSelector.nearest(0, active, x, y, z, 36.0));
    }

    @Test
    void keepsFirstPartnerOnEqualDistance() {
        boolean[] active = {true, true, true};
        double[] x = {0, 2, -2}, y = {0, 0, 0}, z = {0, 0, 0};
        assertEquals(1, SocialPartnerSelector.nearest(0, active, x, y, z, 36.0));
    }

    @Test
    void handlesInvalidOrInactiveSource() {
        boolean[] active = {false, true};
        double[] x = {0, 1}, y = {0, 0}, z = {0, 0};
        assertEquals(-1, SocialPartnerSelector.nearest(0, active, x, y, z, 36.0));
        assertEquals(-1, SocialPartnerSelector.nearest(-1, active, x, y, z, 36.0));
    }
}
