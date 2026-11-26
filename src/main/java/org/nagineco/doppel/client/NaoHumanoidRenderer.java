package org.nagineco.doppel.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;

/** Visual adapter only: the server continues to own NPC simulation. */
public final class NaoHumanoidRenderer extends LivingEntityRenderer<ArmorStandEntity, BipedEntityModel<ArmorStandEntity>> {
    public NaoHumanoidRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5F);
    }

    static java.util.UUID skinOwner(ArmorStandEntity entity) {
        if (entity.getCustomName() == null) return null;
        String marker = entity.getCustomName().getStyle().getInsertion();
        if (marker == null) return null;
        String[] parts = marker.split(":", 4);
        if (parts.length != 4 || !parts[0].equals("dnms") || !parts[1].equals("npc")) return null;
        try {
            int slot = Integer.parseInt(parts[2]);
            if (slot < 1 || slot > 256 || !entity.getCustomName().getString().equals("Doppel " + slot)) return null;
            return java.util.UUID.fromString(parts[3]);
        } catch (IllegalArgumentException ex) { return null; }
    }

    @Override
    public Identifier getTexture(ArmorStandEntity entity) {
        java.util.UUID skinOwner = skinOwner(entity);
        if (skinOwner == null) skinOwner = entity.getUuid();
        var network = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler();
        if (network != null) {
            var entry = network.getPlayerListEntry(skinOwner);
            if (entry != null) return entry.getSkinTextures().texture();
        }
        return DefaultSkinHelper.getSkinTextures(skinOwner).texture();
    }
}
