package org.nagineco.doppel;

import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * Conservative goal-driven movement for armor-stand stand-ins.
 * This does not mine, place blocks, use inventories or simulate full player entities.
 */
public final class NaoLifeAI {
    private NaoLifeAI() {}
    public static NaoLifeStore.Goal chooseGoal(NaoLifeStore.Nao nao,
            SharedMemoryReader memory, long tick) {
        if (nao.energy < 20) return NaoLifeStore.Goal.REST;
        NaoLifeStore.Goal[] options = { NaoLifeStore.Goal.EXPLORE,
            NaoLifeStore.Goal.COLLECT, NaoLifeStore.Goal.BUILD,
            NaoLifeStore.Goal.SOCIAL, NaoLifeStore.Goal.SAFETY };
        // Shared experience is equally accessible; personal energy/skill differs.
        int best = 0;
        double score = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < options.length; i++) {
            NaoLifeStore.Goal goal = options[i];
            int skill = switch (goal) {
                case EXPLORE -> nao.exploration;
                case COLLECT -> nao.collection;
                case BUILD -> nao.building;
                case SOCIAL -> nao.social;
                default -> 0;
            };
            double candidate = Math.log1p(memory.evidence(goal)) * 0.5
                + Math.log1p(skill) * 0.15
                + ((tick / 1200 + nao.number * 7L + i * 13L) % 17) * 0.1;
            if (candidate > score) { score = candidate; best = i; }
        }
        return options[best];
    }
    public static HabitBrain.Move move(NaoLifeStore.Nao nao, List<HabitBrain> brains,
            int index, int tick, PersonalityMemoryStore personality,
            boolean rewardEnabled) {
        if (nao.goal == NaoLifeStore.Goal.REST) return HabitBrain.Move.STAY;
        HabitBrain.Move learned = HabitBrain.sharedDecision(brains, index, tick, personality, rewardEnabled);
        if (learned != HabitBrain.Move.STAY) return learned;
        HabitBrain.Move[] directions = { HabitBrain.Move.NORTH, HabitBrain.Move.EAST,
            HabitBrain.Move.SOUTH, HabitBrain.Move.WEST };
        return directions[Math.floorMod((tick / 80) + index, directions.length)];
    }
    public static void update(NaoLifeStore.Nao nao, boolean moved) {
        if (nao.goal == NaoLifeStore.Goal.REST) nao.energy = Math.min(100, nao.energy + 2);
        else if (moved) nao.energy = Math.max(0, nao.energy - 1);
    }
}
