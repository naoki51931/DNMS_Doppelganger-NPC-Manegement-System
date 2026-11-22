package org.nagineco.doppel;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.Optional;

/** Conservative opt-in harvesting: naturally exposed logs only; no drops or tools simulated. */
public final class NaoWorkPatch {
    private NaoWorkPatch() {}
    public static Optional<BlockPos> findLog(ServerWorld world, Vec3d origin) {
        BlockPos center = BlockPos.ofFloored(origin);
        for (int radius = 1; radius <= 4; radius++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos p = center.add(dx, dy, dz);
                        if (!world.isChunkLoaded(p)) continue;
                        if (!allowed(world.getBlockState(p))) continue;
                        if (world.getBlockState(p.up()).isAir()
                            || world.getBlockState(p.north()).isAir()
                            || world.getBlockState(p.south()).isAir()
                            || world.getBlockState(p.east()).isAir()
                            || world.getBlockState(p.west()).isAir())
                            return Optional.of(p.toImmutable());
                    }
        return Optional.empty();
    }
    private static boolean allowed(BlockState state) {
        return state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG)
            || state.isOf(Blocks.SPRUCE_LOG) || state.isOf(Blocks.JUNGLE_LOG)
            || state.isOf(Blocks.ACACIA_LOG) || state.isOf(Blocks.DARK_OAK_LOG)
            || state.isOf(Blocks.MANGROVE_LOG) || state.isOf(Blocks.CHERRY_LOG);
    }
    public static boolean harvest(ServerWorld world, Vec3d origin, NaoLifeStore.Nao nao) {
        if (nao.inventory == null) nao.inventory = new java.util.HashMap<>();
        if (nao.inventory.values().stream().mapToInt(Integer::intValue).sum() >= 64) return false;
        var target = findLog(world, origin);
        if (target.isEmpty()) return false;
        BlockPos pos = target.get();
        if (Vec3d.ofCenter(pos).squaredDistanceTo(origin) > 16) return false;
        BlockState state = world.getBlockState(pos);
        if (!allowed(state)) return false;
        String key = Registries.BLOCK.getId(state.getBlock()).toString();
        if (!world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3)) return false;
        nao.inventory.merge(key, 1, Integer::sum);
        nao.collection++;
        nao.energy = Math.max(0, nao.energy - 2);
        nao.lastAction = "HARVEST";
        nao.lastOutcome = "COLLECTED " + key + " at " + pos.toShortString();
        return true;
    }

    /**
     * Conservative construction: place one previously harvested log on a nearby
     * solid surface. Never overwrite an occupied block or create free materials.
     */
    public static boolean build(ServerWorld world, Vec3d origin, NaoLifeStore.Nao nao) {
        if (nao.inventory == null || nao.inventory.isEmpty() || nao.energy < 20) return false;
        java.util.List<String> keys = new java.util.ArrayList<>(nao.inventory.keySet());
        java.util.Collections.sort(keys);
        for (String key : keys) {
            if (nao.inventory.getOrDefault(key, 0) <= 0) continue;
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(key);
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            net.minecraft.block.Block block = Registries.BLOCK.get(id);
            if (!allowed(block.getDefaultState())) continue;
            BlockPos center = BlockPos.ofFloored(origin);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos target = center.add(dx, 0, dz);
                    BlockPos support = target.down();
                    if (!world.isChunkLoaded(target) || !world.isChunkLoaded(support)) continue;
                    if (!world.getBlockState(target).isAir()) continue;
                    if (!world.getBlockState(support).isSolidBlock(world, support)) continue;
                    if (Vec3d.ofCenter(target).squaredDistanceTo(origin) > 16) continue;
                    // Avoid building into the NPC's own occupied block.
                    if (target.equals(center)) continue;
                    if (!world.getOtherEntities(null, new net.minecraft.util.math.Box(target)).isEmpty()) continue;
                    if (!world.setBlockState(target, block.getDefaultState(), 3)) continue;
                    nao.inventory.compute(key, (k, count) -> count == null || count <= 1 ? null : count - 1);
                    nao.building++;
                    nao.energy = Math.max(0, nao.energy - 2);
                    nao.lastAction = "BUILD";
                    nao.lastOutcome = "BUILT " + key + " at " + target.toShortString();
                    return true;
                }
        }
        return false;
    }
}
