package org.nagineco.doppel.core;

/** Portable construction workflow: keep the building goal across gathering trips. */
public final class NaoConstructionWorkflow {
    private NaoConstructionWorkflow() {}
    public enum Phase { IDLE, GATHER, RETURN, BUILD, COMPLETE }
    public static Phase next(boolean active, int step, int logs, boolean nearSite) {
        if (!active) return Phase.IDLE;
        if (step < 0 || step > NaoBuildingPlan.starter().size() || logs < 0)
            throw new IllegalArgumentException("Invalid construction state");
        if (step == NaoBuildingPlan.starter().size()) return Phase.COMPLETE;
        if (logs == 0) return Phase.GATHER;
        return nearSite ? Phase.BUILD : Phase.RETURN;
    }
    public static boolean near(double x, double z, double siteX, double siteZ) {
        return Double.isFinite(x) && Double.isFinite(z) &&
            Double.isFinite(siteX) && Double.isFinite(siteZ) &&
            Math.hypot(x - siteX, z - siteZ) <= 2.5;
    }
}
