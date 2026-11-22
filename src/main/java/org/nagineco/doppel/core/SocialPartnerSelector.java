package org.nagineco.doppel.core;

/**
 * Pure NPC social proximity decision. World adapters provide positions and
 * active flags; this class has no dependency on Minecraft entities.
 */
public final class SocialPartnerSelector {
    private SocialPartnerSelector() {}

    /**
     * Returns the nearest active NPC within maxDistanceSquared, or -1.
     * Equal-distance ties keep the earliest index, matching the original loop.
     */
    public static int nearest(int self, boolean[] active,
                              double[] x, double[] y, double[] z,
                              double maxDistanceSquared) {
        if (self < 0 || self >= active.length || !active[self]
                || x.length != active.length || y.length != active.length
                || z.length != active.length)
            return -1;
        int partner = -1;
        double best = maxDistanceSquared;
        for (int j = 0; j < active.length; j++) {
            if (j == self || !active[j]) continue;
            double dx = x[self] - x[j];
            double dy = y[self] - y[j];
            double dz = z[self] - z[j];
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
                partner = j;
            }
        }
        return partner;
    }
}
