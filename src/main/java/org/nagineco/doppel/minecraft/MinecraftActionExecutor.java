package org.nagineco.doppel.minecraft;

import net.minecraft.util.math.Vec3d;
import org.nagineco.doppel.HabitBrain;
import org.nagineco.doppel.core.NpcAction;

/** Minecraft-facing translation and execution of portable movement commands. */
public final class MinecraftActionExecutor {
    private MinecraftActionExecutor() {}

    public static NpcAction fromLegacyMove(HabitBrain.Move move) {
        return new NpcAction(NpcAction.Kind.valueOf(move.name()));
    }

    public static HabitBrain.Move toLegacyMove(NpcAction action) {
        return HabitBrain.Move.valueOf(action.kind().name());
    }

    public static Vec3d proposedPosition(Vec3d origin, NpcAction action) {
        return HabitBrain.applyMove(origin, toLegacyMove(action));
    }
}
