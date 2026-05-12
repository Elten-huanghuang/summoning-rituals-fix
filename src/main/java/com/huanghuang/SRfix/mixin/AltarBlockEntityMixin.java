package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.altar.AltarBlockEntity;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.almostreliable.summoningrituals.recipe.component.RecipeOutputs;
import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.almostreliable.summoningrituals.Registration;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(value = AltarBlockEntity.class, priority = 10000)
public abstract class AltarBlockEntityMixin extends PlatformBlockEntity implements IAltarModeHolder {

    @Unique private boolean yuusha$isAutoMode = false;
    @Unique private ItemStack yuusha$catalystBackup = ItemStack.EMPTY;

    @Shadow(remap = false) @Nullable private AltarRecipe currentRecipe;
    @Shadow(remap = false) private void handleSummoning(AltarRecipe recipe, @Nullable ServerPlayer player) {}
    @Shadow(remap = false) @Nullable private AltarRecipe findRecipe() { return null; }

    public AltarBlockEntityMixin(BlockEntityType<?> t, BlockPos p, BlockState s) { super(t, p, s); }

    @Override public boolean yuusha$isAutoMode() { return this.yuusha$isAutoMode; }
    @Override public void yuusha$setAutoMode(boolean autoMode) { this.yuusha$isAutoMode = autoMode; }

    @Override
    public void yuusha$sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Unique
    private void yuusha$expand() {
        Container inv = this.inventory.getVanillaInv();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("YuushaAmount")) {
                // 1. 还原物理数量
                stack.setCount(stack.getTag().getInt("YuushaAmount"));

                // 🌟 2. 核心修复：还原后必须把旧的记录删掉！
                stack.getTag().remove("YuushaAmount");
                if (stack.getTag().isEmpty()) {
                    stack.setTag(null); // 如果 Tag 空了就彻底清空，防止残留导致匹配失败
                }

                // 3. 重置名字
                stack.resetHoverName();
            }
        }
    }

    @Unique
    private void yuusha$compress() {
        Container inv = this.inventory.getVanillaInv();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                // 🌟 修复核心：优先读取真实数量
                int realAmount = stack.getCount();
                if (stack.hasTag() && stack.getTag().contains("YuushaAmount")) {
                    realAmount = stack.getTag().getInt("YuushaAmount");
                }

                // 根据真实数量来进行判断
                if (realAmount > 1) {
                    stack.getOrCreateTag().putInt("YuushaAmount", realAmount);
                    stack.setCount(1); // 锁定原生数量
                    net.minecraft.network.chat.MutableComponent customName = net.minecraft.network.chat.Component.literal("§e[共 " + realAmount + " 个] §f")
                            .append(net.minecraft.network.chat.Component.translatable(stack.getItem().getDescriptionId()));
                    stack.setHoverName(customName);
                } else if (realAmount == 1) {
                    // 如果真实数量真的只有 1，那才清除 Tag 和名字
                    if (stack.hasTag()) {
                        stack.getTag().remove("YuushaAmount");
                        if (stack.getTag().isEmpty()) stack.setTag(null);
                    }
                    stack.resetHoverName();
                    stack.setCount(1);
                } else {
                    // 数量小于等于 0 则是异常/空位
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Inject(method = "findRecipe", at = @At("HEAD"), remap = false)
    private void preFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.yuusha$expand(); }

    @Inject(method = "findRecipe", at = @At("RETURN"), remap = false)
    private void postFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.yuusha$compress(); }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;handleRecipe(Lcom/almostreliable/summoningrituals/recipe/AltarRecipe;)Z"), remap = false)
    private void preHandleRecipe(CallbackInfo ci) {
        this.yuusha$expand();
        this.yuusha$catalystBackup = this.inventory.getCatalyst().copy();
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lcom/almostreliable/summoningrituals/recipe/component/RecipeOutputs;handleRecipe(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V"),
            remap = false
    )
    private void redirectHandleRecipeOutputs(RecipeOutputs outputs, ServerLevel serverLevel, BlockPos blockPos) {
        if (!this.yuusha$catalystBackup.isEmpty() && this.inventory.getCatalyst().isEmpty()) {
            this.inventory.setCatalyst(this.yuusha$catalystBackup.copy());
            this.yuusha$sync();
        }

        outputs.handleRecipe(serverLevel, blockPos);

        if (!this.yuusha$catalystBackup.isEmpty()) {
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(blockPos).inflate(2.0);
            List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class, area);
            for (ItemEntity itemEntity : items) {
                if (itemEntity.tickCount <= 1 && itemEntity.getItem().getItem() == this.yuusha$catalystBackup.getItem()) {
                    itemEntity.discard();
                }
            }
        }

        this.yuusha$compress();
        this.yuusha$catalystBackup = ItemStack.EMPTY;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private boolean controlAutoTick(ItemStack instance) {
        return !this.yuusha$isAutoMode || instance.isEmpty();
    }

    @Unique
    private boolean yuusha$canMerge(ItemStack inSlot, ItemStack incoming) {
        if (inSlot.isEmpty() || incoming.isEmpty()) return false;
        if (inSlot.getItem() != incoming.getItem()) return false;
        CompoundTag tagA = inSlot.getTag() != null ? inSlot.getTag().copy() : null;
        CompoundTag tagB = incoming.getTag() != null ? incoming.getTag().copy() : null;

        if (tagA != null) { tagA.remove("YuushaAmount"); tagA.remove("display"); if (tagA.isEmpty()) tagA = null; }
        if (tagB != null) { tagB.remove("YuushaAmount"); tagB.remove("display"); if (tagB.isEmpty()) tagB = null; }

        if (tagA == null && tagB == null) return true;
        if (tagA == null || tagB == null) return false;
        return tagA.equals(tagB);
    }

    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void onHandleInteraction(@Nullable ServerPlayer player, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (this.progress > 0 || player == null) return;
        Container inv = this.inventory.getVanillaInv();

        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                boolean removed = false;
                for (int i = inv.getContainerSize() - 1; i >= 0; i--) {
                    ItemStack inSlot = inv.getItem(i);
                    if (!inSlot.isEmpty()) {
                        int realCount = (inSlot.hasTag() && inSlot.getTag().contains("YuushaAmount"))
                                ? inSlot.getTag().getInt("YuushaAmount") : inSlot.getCount();

                        ItemStack result = inSlot.copy();
                        if (result.hasTag()) {
                            result.getTag().remove("YuushaAmount");
                            if (result.getTag().isEmpty()) result.setTag(null);
                        }
                        result.resetHoverName();
                        inv.setItem(i, ItemStack.EMPTY);

                        while (realCount > 0) {
                            int toGive = Math.min(realCount, result.getMaxStackSize());
                            ItemStack drop = result.copyWithCount(toGive);
                            ItemEntity entity = new ItemEntity(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, drop);
                            entity.setDefaultPickUpDelay();
                            this.level.addFreshEntity(entity);
                            realCount -= toGive;
                        }
                        removed = true;
                        break;
                    }
                }

                if (!removed && !this.inventory.getCatalyst().isEmpty()) {
                    ItemStack catalyst = this.inventory.getCatalyst().copy();
                    this.inventory.setCatalyst(ItemStack.EMPTY);
                    ItemEntity entity = new ItemEntity(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, catalyst);
                    entity.setDefaultPickUpDelay();
                    this.level.addFreshEntity(entity);
                    removed = true;
                }

                if (removed) {
                    this.yuusha$compress(); // 修复点：移除物品后也更新显示状态
                    this.yuusha$sync();
                }
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            else if (!this.yuusha$isAutoMode) {
                AltarRecipe recipe = this.findRecipe();
                if (recipe != null) {
                    this.handleSummoning(recipe, player);
                    this.yuusha$sync();
                }
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }
        else {
            if (!com.huanghuang.SRfix.util.SRfixConfig.isAllowed(stack)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("祭坛排斥了这件物品！").withStyle(net.minecraft.ChatFormatting.RED),
                        true
                );
                cir.setReturnValue(stack);
                return;
            }

            boolean isCatalyst = AltarRecipe.CATALYST_CACHE.stream().anyMatch(ing -> ing.test(stack));
            if (isCatalyst) {
                this.inventory.setCatalyst(stack.copyWithCount(1));
                this.yuusha$compress(); // 修复点：放入催化剂后立即更新显示
                if (this.yuusha$isAutoMode) {
                    AltarRecipe recipe = this.findRecipe();
                    if (recipe != null) this.handleSummoning(recipe, player);
                }
                this.yuusha$sync();
                ItemStack rem = stack.copy(); rem.shrink(1);
                cir.setReturnValue(rem);
                return;
            }

            boolean merged = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack inSlot = inv.getItem(i);
                if (yuusha$canMerge(inSlot, stack)) {
                    int current = inSlot.getCount();
                    if (inSlot.hasTag() && inSlot.getTag().contains("YuushaAmount")) {
                        current = inSlot.getTag().getInt("YuushaAmount");
                    }
                    inSlot.getOrCreateTag().putInt("YuushaAmount", current + stack.getCount());
                    inSlot.setCount(1);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).isEmpty()) {
                        ItemStack toIn = stack.copy();
                        toIn.getOrCreateTag().putInt("YuushaAmount", stack.getCount());
                        toIn.setCount(1);
                        inv.setItem(i, toIn);
                        merged = true;
                        break;
                    }
                }
            }

            if (merged) {
                this.yuusha$compress(); // 修复点：合并或存入普通材料后立即更新显示
                this.yuusha$sync();
                if (this.yuusha$isAutoMode && !this.inventory.getCatalyst().isEmpty()) {
                    AltarRecipe recipe = this.findRecipe();
                    if (recipe != null) this.handleSummoning(recipe, player);
                }
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPlayerDestroy(boolean creative, CallbackInfo ci) {
        if (this.level == null || this.level.isClientSide) return;
        Container inv = this.inventory.getVanillaInv();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                int amount = stack.getCount();
                if (stack.hasTag() && stack.getTag().contains("YuushaAmount")) {
                    amount = stack.getTag().getInt("YuushaAmount");
                }
                ItemStack base = stack.copy();
                if (base.hasTag()) {
                    base.getTag().remove("YuushaAmount");
                    if (base.getTag().isEmpty()) base.setTag(null);
                }
                base.resetHoverName();
                while (amount > 0) {
                    int count = Math.min(amount, 64);
                    this.level.addFreshEntity(new ItemEntity(level, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, base.copyWithCount(count)));
                    amount -= count;
                }
            }
        }
        if (!this.inventory.getCatalyst().isEmpty()) {
            this.level.addFreshEntity(new ItemEntity(level, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, this.inventory.getCatalyst()));
        }
        if (!creative) {
            this.level.addFreshEntity(new ItemEntity(level, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, new ItemStack((ItemLike) Registration.ALTAR_ITEM.get())));
        }
        ci.cancel();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("YuushaAutoMode", this.yuusha$isAutoMode);
        tag.remove("permission_to_damage");
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        this.yuusha$isAutoMode = tag.getBoolean("YuushaAutoMode");
        tag.remove("permission_to_damage");
    }
}