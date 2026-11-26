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
public abstract class ArmorStandRendererMixin {
    @Unique private NaoHumanoidRenderer dnms$renderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dnms$init(EntityRendererFactory.Context context, CallbackInfo ci) {
        dnms$renderer = new NaoHumanoidRenderer(context);
    }

    @Inject(method = "render(Lnet/minecraft/entity/decoration/ArmorStandEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void dnms$render(ArmorStandEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                             VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (entity.getCustomName() == null || !java.util.Objects.equals(entity.getCustomName().getStyle().getInsertion(), "dnms:npc:" + entity.getCustomName().getString().replaceFirst("^Doppel ", ""))) return;
        dnms$renderer.render(entity, yaw, tickDelta, matrices, vertices, light);
        ci.cancel();
    }
}
