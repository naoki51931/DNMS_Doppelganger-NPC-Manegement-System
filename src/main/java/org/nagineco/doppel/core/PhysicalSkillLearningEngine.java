package org.nagineco.doppel.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable physical skill learning and transfer. The shared model is a source
 * of technique/capacity values; individual slots remain independently addressable.
 */
public final class PhysicalSkillLearningEngine {
    private static final PhysicalSkillSync.Skill ZERO =
        new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0);
    private final List<PhysicalSkillSync.Skill> states;
    private PhysicalSkillSync.Mode mode;
    private double transferRatio;

    public PhysicalSkillLearningEngine(int count, PhysicalSkillSync.Mode mode, double ratio) {
        if (count < 1 || count > 256 || mode == null
                || !Double.isFinite(ratio) || ratio < 0 || ratio > 1)
            throw new IllegalArgumentException("Invalid physical skill engine settings");
        states = new ArrayList<>(java.util.Collections.nCopies(count, ZERO));
        this.mode = mode;
        this.transferRatio = ratio;
    }

    public int count() { return states.size(); }
    public PhysicalSkillSync.Skill state(int slot) { return states.get(slot); }
    public PhysicalSkillSync.Mode mode() { return mode; }

    public void configure(PhysicalSkillSync.Mode nextMode, double ratio) {
        if (nextMode == null || !Double.isFinite(ratio) || ratio < 0 || ratio > 1)
            throw new IllegalArgumentException("Invalid synchronization configuration");
        mode = nextMode;
        transferRatio = ratio;
    }

    /**
     * Successful practice increases technique, coordination and proficiency.
     * Strength, agility and endurance increase according to practice intensity.
     * Each gain is capped at 100, never decreases existing skill.
     */
    public void practice(int slot, double minutes, double success, double intensity) {
        if (!Double.isFinite(minutes) || minutes < 0 || minutes > 1440
                || !Double.isFinite(success) || success < 0 || success > 1
                || !Double.isFinite(intensity) || intensity < 0 || intensity > 1)
            throw new IllegalArgumentException("Invalid practice evidence");
        PhysicalSkillSync.Skill current = states.get(slot);
        double effort = minutes / 60.0;
        double techniqueGain = effort * success;
        double physicalGain = effort * intensity * 0.5;
        PhysicalSkillSync.Skill learned = new PhysicalSkillSync.Skill(
            cap(current.technique() + techniqueGain),
            cap(current.coordination() + techniqueGain * 0.7),
            cap(current.proficiency() + techniqueGain * 0.8),
            cap(current.strength() + physicalGain),
            cap(current.agility() + physicalGain * 0.8),
            cap(current.endurance() + physicalGain * 1.2));
        states.set(slot, learned);
        if (mode != PhysicalSkillSync.Mode.INDIVIDUAL && transferRatio > 0) {
            for (int i = 0; i < states.size(); i++) {
                if (i != slot) states.set(i,
                    PhysicalSkillSync.transfer(learned, states.get(i), mode, transferRatio));
            }
        }
    }

    /** Restore a checkpoint without triggering any synchronization. */
    public void restore(List<PhysicalSkillSync.Skill> saved) {
        if (saved == null || saved.size() != states.size() || saved.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Incompatible physical skill snapshot");
        for (int i = 0; i < states.size(); i++) states.set(i, saved.get(i));
    }

    public List<PhysicalSkillSync.Skill> snapshot() { return List.copyOf(states); }

    private static double cap(double value) { return Math.min(100, value); }
}
