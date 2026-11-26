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

    @Override
    public Identifier getTexture(ArmorStandEntity entity) {
        return DefaultSkinHelper.getSkinTextures(entity.getUuid()).texture();
    }
}
