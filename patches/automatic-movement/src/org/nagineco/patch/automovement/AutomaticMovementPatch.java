package org.nagineco.patch.automovement;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.nagineco.doppel.HabitBrain;
import org.nagineco.doppel.PersonalityMemoryStore;
import java.util.List;

/** AMP: server-side assisted movement using the same shared Nao personality as NPCs. */
public final class AutomaticMovementPatch {
    private AutomaticMovementPatch() {}

    public static HabitBrain.Move choose(List<HabitBrain> brains, int playableIndex,
                                         int dayTick, PersonalityMemoryStore personality) {
        if (personality == null || brains.isEmpty()) return HabitBrain.Move.STAY;
        return HabitBrain.sharedDecision(brains,
            Math.max(0, Math.min(playableIndex, brains.size() - 1)), dayTick, personality);
    }

    /** Apply only horizontal motion; retain vertical physics and avoid solid destination blocks. */
    public static boolean move(ServerPlayerEntity player, HabitBrain.Move direction) {
        if (direction == HabitBrain.Move.STAY) return false;
        Vec3d delta = HabitBrain.applyMove(Vec3d.ZERO, direction);
        Vec3d target = player.getPos().add(delta);
        if (!player.getServerWorld().isAir(net.minecraft.util.math.BlockPos.ofFloored(target))
            || !player.getServerWorld().isAir(net.minecraft.util.math.BlockPos.ofFloored(target.add(0, 1, 0))))
            return false;
        Vec3d old = player.getVelocity();
        player.setVelocity(delta.x, old.y, delta.z);
        player.velocityModified = true;
        return true;
    }
}
