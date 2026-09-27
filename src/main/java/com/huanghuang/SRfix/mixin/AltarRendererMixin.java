package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.altar.AltarBlockEntity;
import com.almostreliable.summoningrituals.altar.AltarRenderer;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AltarRenderer.class, remap = false)
public abstract class AltarRendererMixin {

    @Unique private static final float huanghuang$LABEL_Y = 1.5f;
    @Unique private static final float huanghuang$SCALE = 0.02f;

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void huanghuang$renderModeLabel(AltarBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                         MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                         CallbackInfo ci) {
        if (!(blockEntity instanceof IAltarModeHolder holder)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String text = holder.huanghuang$isAutoMode() ? "§6§l全自动" : "§7手动";
        Font font = mc.font;

        poseStack.pushPose();
        poseStack.translate(0.5, huanghuang$LABEL_Y, 0.5);

        Camera camera = mc.gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-huanghuang$SCALE, -huanghuang$SCALE, huanghuang$SCALE);

        float w = font.width(text) / 2f;
        font.drawInBatch(text, -w, 0, 0xFFFFFF, false,
                poseStack.last().pose(), bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0x40000000, packedLight);

        poseStack.popPose();
    }
}
