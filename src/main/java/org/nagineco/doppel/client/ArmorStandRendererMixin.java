package org.nagineco.doppel.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandEntityRenderer.class)
public abstract class ArmorStandRendererMixin implements NaoRendererAccess {
    @Unique private NaoHumanoidRenderer dnms$wide;
    @Unique private NaoHumanoidRenderer dnms$slim;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dnms$init(EntityRendererFactory.Context context, CallbackInfo ci) {
        dnms$wide = new NaoHumanoidRenderer(context, false);
        dnms$slim = new NaoHumanoidRenderer(context, true);
    }
    @Override
    public void dnms$render(ArmorStandEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertices, int light) {
        var renderer = NaoHumanoidRenderer.skin(entity).model() == net.minecraft.client.util.SkinTextures.Model.SLIM
            ? dnms$slim : dnms$wide;
        renderer.render(entity, yaw, tickDelta, matrices, vertices, light);
    }
}
