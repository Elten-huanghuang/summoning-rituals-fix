package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.inventory.AltarInventory;
import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AltarInventory.class, remap = false)
public abstract class AltarInventoryMixin {

    @Shadow private NonNullList<ItemStack> items;
    @Shadow private ItemStack catalyst;
    @Shadow private PlatformBlockEntity parent;
    @Shadow private void onContentsChanged() {}
    @Shadow private void validateSlot(int slot) {}

    @Unique private ItemStack yuusha$catalystBackup = ItemStack.EMPTY;

    // ========== handleRecipe 催化剂保护（最内层防线）==========

    @Inject(method = "handleRecipe", at = @At("HEAD"), remap = false)
    private void yuusha$preHandleRecipe(AltarRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        this.yuusha$catalystBackup = this.catalyst.copy();
    }

    @Inject(method = "handleRecipe", at = @At("RETURN"), remap = false)
    private void yuusha$postHandleRecipe(AltarRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (!this.yuusha$catalystBackup.isEmpty() && this.catalyst.isEmpty()) {
            this.catalyst = this.yuusha$catalystBackup.copy();
            this.onContentsChanged();
        }
        this.yuusha$catalystBackup = ItemStack.EMPTY;
    }

    // ========== popLastInserted：手动模式提取顺序 + 自动模式催化剂保护 ==========

    @Inject(method = "popLastInserted", at = @At("HEAD"), cancellable = true, remap = false)
    private void yuusha$prePopLastInserted(CallbackInfo ci) {
        if (!(this.parent instanceof IAltarModeHolder holder)) return;

        if (holder.yuusha$isAutoMode()) {
            // 自动模式：暂时隐藏催化剂，让原方法弹出材料，RETURN 注入恢复催化剂
            if (!this.catalyst.isEmpty()) {
                this.yuusha$catalystBackup = this.catalyst.copy();
                this.catalyst = ItemStack.EMPTY;
            }
            return; // 不取消，让原方法执行
        }

        // 手动模式：完全接管，实现 普通材料 → 骨灰 → 催化剂 顺序
        ci.cancel();
        Level level = this.parent.getLevel();
        BlockPos pos = this.parent.getBlockPos();
        if (level == null || level.isClientSide) return;

        // 第一轮：弹出普通材料（记录骨灰位置，不弹出）
        int hexAshIndex = -1;
        for (int i = this.items.size() - 1; i >= 0; i--) {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && id.toString().equals("malum:hex_ash")) {
                    hexAshIndex = i;
                } else {
                    yuusha$popFromSlot(i, level, pos);
                    return;
                }
            }
        }

        // 第二轮：普通材料没了，弹出骨灰
        if (hexAshIndex != -1) {
            yuusha$popFromSlot(hexAshIndex, level, pos);
            return;
        }

        // 第三轮：骨灰也没了，最后弹出催化剂
        if (!this.catalyst.isEmpty()) {
            yuusha$dropStack(this.catalyst, level, pos);
            this.catalyst = ItemStack.EMPTY;
            this.onContentsChanged();
        }
    }

    @Inject(method = "popLastInserted", at = @At("RETURN"), remap = false)
    private void yuusha$postPopLastInserted(CallbackInfo ci) {
        // 仅自动模式需要恢复催化剂（手动模式已被 cancel，不会执行到此）
        if (!this.yuusha$catalystBackup.isEmpty()) {
            this.catalyst = this.yuusha$catalystBackup;
            this.yuusha$catalystBackup = ItemStack.EMPTY;
            this.onContentsChanged();
        }
    }

    @Unique
    private void yuusha$popFromSlot(int slot, Level level, BlockPos pos) {
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) return;
        yuusha$dropStack(stack, level, pos);
        this.items.set(slot, ItemStack.EMPTY);
        this.onContentsChanged();
    }

    @Unique
    private void yuusha$dropStack(ItemStack stack, Level level, BlockPos pos) {
        int amount = stack.getCount();
        if (stack.hasTag() && stack.getTag().contains("YuushaAmount")) {
            amount = stack.getTag().getInt("YuushaAmount");
        }
        ItemStack clean = stack.copy();
        if (clean.hasTag()) {
            clean.getTag().remove("YuushaAmount");
            clean.getTag().remove("display");
            if (clean.getTag().isEmpty()) clean.setTag(null);
        }
        clean.resetHoverName();
        while (amount > 0) {
            int count = Math.min(amount, clean.getMaxStackSize());
            ItemEntity entity = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    clean.copyWithCount(count));
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
            amount -= count;
        }
    }

    // ========== IItemHandler 接口拦截（AE2/RS/管道/漏斗）==========

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

    @Unique
    private void yuusha$updateStackDisplay(ItemStack stack, int amount) {
        if (amount > 1) {
            stack.getOrCreateTag().putInt("YuushaAmount", amount);
            stack.setCount(1);
            net.minecraft.network.chat.MutableComponent customName = net.minecraft.network.chat.Component.literal("§e[共 " + amount + " 个] §f")
                    .append(net.minecraft.network.chat.Component.translatable(stack.getItem().getDescriptionId()));
            stack.setHoverName(customName);
        } else {
            if (stack.hasTag()) {
                stack.getTag().remove("YuushaAmount");
                if (stack.getTag().isEmpty()) stack.setTag(null);
            }
            stack.resetHoverName();
            stack.setCount(1);
        }
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void yuusha$onExtractItem(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        this.validateSlot(slot);

        if (slot == 64) return; // 催化剂槽走原版

        ItemStack currentStack = this.items.get(slot);
        if (currentStack.isEmpty() || amount <= 0) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int currentAmount = currentStack.getCount();
        if (currentStack.hasTag() && currentStack.getTag().contains("YuushaAmount")) {
            currentAmount = currentStack.getTag().getInt("YuushaAmount");
        }

        int toExtract = Math.min(amount, currentAmount);
        ItemStack extracted = currentStack.copyWithCount(toExtract);
        if (extracted.hasTag()) {
            extracted.getTag().remove("YuushaAmount");
            if (extracted.getTag().isEmpty()) extracted.setTag(null);
        }
        extracted.resetHoverName();

        if (!simulate) {
            int remaining = currentAmount - toExtract;
            if (remaining > 0) {
                this.yuusha$updateStackDisplay(currentStack, remaining);
            } else {
                this.items.set(slot, ItemStack.EMPTY);
            }
            this.onContentsChanged();
        }

        cir.setReturnValue(extracted);
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void yuusha$onInsertItem(int slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (stack.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        this.validateSlot(slot);

        // 催化剂槽：不做 NBT 修改，保持纯净渲染
        if (slot == 64) {
            if (this.catalyst.isEmpty()) {
                this.catalyst = stack.copyWithCount(1);
                this.onContentsChanged();
                cir.setReturnValue(ItemStack.EMPTY);
            }
            return; // 槽位已有催化剂则不吞物品，原方法会退回
        }

        ItemStack currentStack = this.items.get(slot);

        if (currentStack.isEmpty()) {
            ItemStack toIn = stack.copy();
            this.yuusha$updateStackDisplay(toIn, stack.getCount());
            this.items.set(slot, toIn);
            this.onContentsChanged();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (this.yuusha$canMerge(currentStack, stack)) {
            int currentAmount = currentStack.getCount();
            if (currentStack.hasTag() && currentStack.getTag().contains("YuushaAmount")) {
                currentAmount = currentStack.getTag().getInt("YuushaAmount");
            }
            this.yuusha$updateStackDisplay(currentStack, currentAmount + stack.getCount());
            this.onContentsChanged();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        cir.setReturnValue(stack);
    }
}