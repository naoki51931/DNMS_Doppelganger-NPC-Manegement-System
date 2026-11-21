package org.nagineco.patch.automovement;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
        if (direction == HabitBrain.Move.STAY || !player.isOnGround()) return false;
        Vec3d delta = HabitBrain.applyMove(Vec3d.ZERO, direction);
        Vec3d target = player.getPos().add(delta);
        // Reject obstacles and any destination without a solid, full-width footing.
        // AABB collision testing is safer than checking only one block's air state.
        Box destination = player.getBoundingBox().offset(delta.x, 0, delta.z);
        if (!player.getServerWorld().isSpaceEmpty(player, destination)) return false;
        Box support = destination.offset(0, -0.12, 0);
        if (player.getServerWorld().isSpaceEmpty(player, support)) return false;
        // Extra guard: reject lava and water at feet and one block ahead.
        BlockPos feet = BlockPos.ofFloored(target);
        if (!player.getServerWorld().getFluidState(feet).isEmpty()) return false;
        Vec3d old = player.getVelocity();
        player.setVelocity(delta.x, old.y, delta.z);
        player.velocityModified = true;
        return true;
    }
}
