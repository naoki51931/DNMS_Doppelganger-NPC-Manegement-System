package org.nagineco.doppel.core;

/** Deterministic, bounded proficiency-based task execution policy. */
public final class PhysicalWorkPerformance {
    private PhysicalWorkPerformance() {}

    /** Collection skill improves the execution probability from 70% to 95%. */
    public static double collectionSuccessProbability(PhysicalSkillSync.Skill skill) {
        if (skill == null) return 0.70;
        double proficiency = (skill.technique() + skill.coordination() + skill.proficiency()) / 300.0;
        return 0.70 + 0.25 * proficiency;
    }

    /**
     * Deterministic pseudo-random trial per attempt, without global RNG state.
     * A failed skill trial does not modify the Minecraft world.
     */
    public static boolean canExecuteCollection(PhysicalSkillSync.Skill skill, int npcSlot, long attempt) {
        if (npcSlot < 0 || npcSlot >= 256 || attempt < 0)
            throw new IllegalArgumentException("Invalid NPC collection attempt");
        long z = attempt * 0x9E3779B97F4A7C15L + (npcSlot + 1L) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        double roll = (z >>> 11) * 0x1.0p-53;
        return roll < collectionSuccessProbability(skill);
    }
}
