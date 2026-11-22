package org.nagineco.doppel.core;

/**
 * Bounded movement performance derived from learned physical skills.
 * Independent of Minecraft, so the same policy can be used by a future C++ adapter.
 */
public final class PhysicalMovementPerformance {
    private PhysicalMovementPerformance() {}
    public static final double BASE_STEP = 0.12;

    /** At most 50% faster than the baseline; a zero-skilled NPC is unchanged. */
    public static double stepDistance(PhysicalSkillSync.Skill skill) {
        if (skill == null) return BASE_STEP;
        double mobility = (skill.agility() + skill.coordination() + skill.endurance()) / 300.0;
        return BASE_STEP * (1.0 + 0.5 * mobility);
    }

    /** Only genuine successful travel is converted to practice evidence. */
    public static double practiceMinutes(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) return 0;
        return Math.min(1.0, distance / BASE_STEP / 1200.0);
    }
}
