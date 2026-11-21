package org.nagineco.doppel;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import java.util.*;

/**
 * In-memory bounded block-state snapshot. Only loaded chunks; NO block entities,
 * entities, scheduled ticks, lighting, fluids, or cross-restart persistence.
 * Never advertise this as full-world rewind.
 */
public final class NaoRegionRollback {
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    private final ServerWorld world;
    private NaoRegionRollback(ServerWorld world) { this.world = world; }

    public static NaoRegionRollback capture(ServerWorld world, BlockPos center, int radius, int halfHeight) {
        if (radius < 1 || radius > 12 || halfHeight < 1 || halfHeight > 8)
            throw new IllegalArgumentException("region too large");
        NaoRegionRollback result = new NaoRegionRollback(world);
        for (int x = -radius; x <= radius; x++)
            for (int y = -halfHeight; y <= halfHeight; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x,y,z);
                    if (!world.isChunkLoaded(pos))
                        throw new IllegalStateException("snapshot region contains unloaded chunks");
                    if (world.getBlockEntity(pos) != null)
                        throw new IllegalStateException("snapshot region contains a block entity: " + pos);
                    result.original.put(pos.toImmutable(), world.getBlockState(pos));
                }
        return result;
    }
    /** Restore blocks only; refuse unloaded chunks and newly introduced block entities. */
    public int restore() {
        for (BlockPos pos : original.keySet()) {
            if (!world.isChunkLoaded(pos) || world.getBlockEntity(pos) != null)
                throw new IllegalStateException("cannot safely restore region at " + pos);
        }
        int changed = 0;
        for (var entry : original.entrySet()) {
            if (!world.getBlockState(entry.getKey()).equals(entry.getValue())) {
                if (!world.setBlockState(entry.getKey(), entry.getValue(), 3))
                    throw new IllegalStateException("block restore failed at " + entry.getKey());
                changed++;
            }
        }
        return changed;
    }
    public int size() { return original.size(); }
}
