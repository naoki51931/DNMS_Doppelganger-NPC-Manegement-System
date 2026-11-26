package org.nagineco.doppel.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;

/** Visual adapter only: the server continues to own NPC simulation. */
public final class NaoHumanoidRenderer extends LivingEntityRenderer<ArmorStandEntity, PlayerEntityModel<ArmorStandEntity>> {
    public NaoHumanoidRenderer(EntityRendererFactory.Context context, boolean slim) {
        super(context, new NaoPlayerModel(context.getPart(slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER), slim), 0.5F);
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

    private static final java.util.Map<java.util.UUID, net.minecraft.client.util.SkinTextures> CACHE = new java.util.LinkedHashMap<>(16, 0.75f, true);

    static net.minecraft.client.util.SkinTextures skin(ArmorStandEntity entity) {
        java.util.UUID skinOwner = skinOwner(entity);
        if (skinOwner == null) skinOwner = entity.getUuid();
        var network = net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler();
        if (network != null) {
            var entry = network.getPlayerListEntry(skinOwner);
            if (entry != null) {
                var skin = entry.getSkinTextures();
                CACHE.put(skinOwner, skin);
                NaoSkinProfileStore.save(skinOwner, entry.getProfile());
                if (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
                return skin;
            }
        }
        var cached = CACHE.get(skinOwner);
        if (cached != null) return cached;
        return NaoSkinProfileStore.resolve(skinOwner);
    }
    @Override
    public Identifier getTexture(ArmorStandEntity entity) { return skin(entity).texture(); }
}
