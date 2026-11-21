package org.nagineco.doppel;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
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
    public static boolean danger(ServerWorld world, Vec3d pos) {
        BlockPos feet = BlockPos.ofFloored(pos);
        BlockPos below = feet.down();
        return !world.getFluidState(feet).isEmpty()
            || !world.getFluidState(below).isEmpty()
            || !world.getBlockState(below).isSolidBlock(world, below);
    }
    public static void plan(NaoLifeStore.Nao nao, SharedMemoryReader memory,
                            ServerWorld world, Vec3d position, long tick) {
        if (danger(world, position) || nao.blockedSteps >= 8) {
            nao.goal = NaoLifeStore.Goal.SAFETY;
            nao.goalUntilTick = tick + 100;
            nao.blockedSteps = 0;
        } else if (nao.energy < 20) {
            nao.goal = NaoLifeStore.Goal.REST;
            nao.goalUntilTick = tick + 200;
        } else if (tick >= nao.goalUntilTick
                || (nao.goal == NaoLifeStore.Goal.REST && nao.energy >= 80)) {
            nao.goal = chooseGoal(nao, memory, tick);
            nao.goalUntilTick = tick + 1200;
        }
        nao.decisions++;
    }
    public static HabitBrain.Move move(NaoLifeStore.Nao nao, List<HabitBrain> brains,
            int index, int tick, PersonalityMemoryStore personality,
            boolean rewardEnabled, SharedMemoryReader memory, Vec3d position) {
        if (nao.goal == NaoLifeStore.Goal.REST || nao.goal == NaoLifeStore.Goal.SAFETY) return HabitBrain.Move.STAY;
        // All individuals can consult a nearby experience, regardless of its original owner.
        if (memory != null && position != null) {
            var nearby = memory.nearest(nao.goal, position, 16.0);
            if (nearby.isPresent()) return nearby.get().action();
        }
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
