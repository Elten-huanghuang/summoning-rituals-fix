package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.altar.AltarBlockEntity;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeMixin {

    @Unique
    private static final Logger huanghuang$LOGGER = LogUtils.getLogger();

    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void huanghuang$onLeftClickAltar(BlockPos pos, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action action, net.minecraft.core.Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
        try {
            if (action == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK && player.isShiftKeyDown()) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof AltarBlockEntity altar) {
                    IAltarModeHolder holder = (IAltarModeHolder) altar;
                    boolean newMode = !holder.huanghuang$isAutoMode();
                    holder.huanghuang$setAutoMode(newMode);
                    holder.huanghuang$sync();

                    String modeName = newMode ? "全自动" : "手动";
                    player.displayClientMessage(Component.literal("祭坛模式: " + modeName).withStyle(ChatFormatting.GOLD), true);
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Failed to handle altar mode switch at {}", pos, e);
        }
    }
}