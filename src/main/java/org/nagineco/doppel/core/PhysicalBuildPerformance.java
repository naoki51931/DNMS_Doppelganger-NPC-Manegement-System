package org.nagineco.doppel.core;

/** Deterministic, skill-dependent construction trial. */
public final class PhysicalBuildPerformance {
    private PhysicalBuildPerformance() {}

    public static double successProbability(PhysicalSkillSync.Skill skill) {
        if (skill == null) return 0.65;
        return 0.65 + 0.30 * (skill.technique() + skill.coordination()
            + skill.proficiency()) / 300.0;
    }

    public static boolean canBuild(PhysicalSkillSync.Skill skill, int slot, long attempt) {
        if (slot < 0 || slot >= 256 || attempt < 0)
            throw new IllegalArgumentException("Invalid construction attempt");
        long z = (attempt + 1) * 0x9E3779B97F4A7C15L
            ^ (slot + 1L) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53 < successProbability(skill);
    }
}
