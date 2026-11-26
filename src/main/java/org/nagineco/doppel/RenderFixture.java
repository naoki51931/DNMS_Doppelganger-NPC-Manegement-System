package org.nagineco.doppel;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

final class RenderFixture {
    private static final List<ArmorStandEntity> active = new ArrayList<>();
    static int replace(ServerCommandSource source, int count) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        active.forEach(ArmorStandEntity::discard);
        active.clear();
        var world = source.getWorld();
        var origin = player.getPos();
        for (int i = 0; i < count; i++) {
            var stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            int slot = i + 1;
            String marker = "dnms:npc:" + slot + ":" + player.getUuid();
            stand.setCustomName(Text.literal("Doppel " + slot).styled(style -> style.withInsertion(marker)));
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.refreshPositionAndAngles(origin.x + (i % 16 - 7.5) * 1.2,
                origin.y, origin.z + 5 + (i / 16) * 1.2, 180, 0);
            if (world.spawnEntity(stand)) active.add(stand);
        }
        int actual = active.size();
        source.sendFeedback(() -> Text.literal("DNMS_RENDER_FIXTURE requested=" + count + " spawned=" + actual), false);
        return actual;
    }
}
