package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.inventory.AltarInventory;
import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import com.huanghuang.SRfix.util.StackAmountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
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

    @Unique private ItemStack huanghuang$catalystBackup = ItemStack.EMPTY;

    // ========== handleRecipe 催化剂保护（最内层防线）==========

    @Inject(method = "handleRecipe", at = @At("HEAD"), remap = false)
    private void huanghuang$preHandleRecipe(AltarRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        this.huanghuang$catalystBackup = this.catalyst.copy();
    }

    @Inject(method = "handleRecipe", at = @At("RETURN"), remap = false)
    private void huanghuang$postHandleRecipe(AltarRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (!this.huanghuang$catalystBackup.isEmpty() && this.catalyst.isEmpty()) {
            this.catalyst = this.huanghuang$catalystBackup.copy();
            this.onContentsChanged();
        }
        this.huanghuang$catalystBackup = ItemStack.EMPTY;
    }

    // ========== popLastInserted：手动模式提取顺序 + 自动模式催化剂保护 ==========

    @Inject(method = "popLastInserted", at = @At("HEAD"), cancellable = true, remap = false)
    private void huanghuang$prePopLastInserted(CallbackInfo ci) {
        if (!(this.parent instanceof IAltarModeHolder holder)) return;

        if (holder.huanghuang$isAutoMode()) {
            // 自定义插入不写入原模组的 insertOrder，因此自动模式也使用槽位扫描。
            ci.cancel();
            Level level = this.parent.getLevel();
            if (level != null && !level.isClientSide) {
                huanghuang$popMaterial(level, this.parent.getBlockPos());
            }
            return;
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
                    huanghuang$popFromSlot(i, level, pos);
                    return;
                }
            }
        }

        // 第二轮：普通材料没了，弹出骨灰
        if (hexAshIndex != -1) {
            huanghuang$popFromSlot(hexAshIndex, level, pos);
            return;
        }

        // 第三轮：骨灰也没了，最后弹出催化剂
        if (!this.catalyst.isEmpty()) {
            huanghuang$dropStack(this.catalyst, level, pos);
            this.catalyst = ItemStack.EMPTY;
            this.onContentsChanged();
        }
    }

    @Unique
    private void huanghuang$popMaterial(Level level, BlockPos pos) {
        for (int i = this.items.size() - 1; i >= 0; i--) {
            if (!this.items.get(i).isEmpty()) {
                huanghuang$popFromSlot(i, level, pos);
                return;
            }
        }
    }

    @Unique
    private void huanghuang$popFromSlot(int slot, Level level, BlockPos pos) {
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) return;
        huanghuang$dropStack(stack, level, pos);
        this.items.set(slot, ItemStack.EMPTY);
        this.onContentsChanged();
    }

    @Unique
    private void huanghuang$dropStack(ItemStack stack, Level level, BlockPos pos) {
        int amount = StackAmountUtil.getAmount(stack);
        ItemStack clean = StackAmountUtil.copyForOutput(stack);
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
    private boolean huanghuang$canMerge(ItemStack inSlot, ItemStack incoming) {
        if (inSlot.isEmpty() || incoming.isEmpty()) return false;
        if (inSlot.getItem() != incoming.getItem()) return false;
        return StackAmountUtil.comparableTag(inSlot).equals(StackAmountUtil.comparableTag(incoming));
    }

    @Unique
    private void huanghuang$updateStackDisplay(ItemStack stack, int amount) {
        StackAmountUtil.setCompressed(stack, amount);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void huanghuang$onExtractItem(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        this.validateSlot(slot);

        if (slot == 64) return; // 催化剂槽走原版

        ItemStack currentStack = this.items.get(slot);
        if (currentStack.isEmpty() || amount <= 0) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int currentAmount = StackAmountUtil.getAmount(currentStack);

        int toExtract = Math.min(Math.min(amount, currentAmount), currentStack.getMaxStackSize());
        ItemStack extracted = StackAmountUtil.copyForOutput(currentStack).copyWithCount(toExtract);

        if (!simulate) {
            int remaining = currentAmount - toExtract;
            if (remaining > 0) {
                this.huanghuang$updateStackDisplay(currentStack, remaining);
            } else {
                this.items.set(slot, ItemStack.EMPTY);
            }
            this.onContentsChanged();
        }

        cir.setReturnValue(extracted);
    }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void huanghuang$onInsertItem(int slot, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
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
            this.huanghuang$updateStackDisplay(toIn, stack.getCount());
            this.items.set(slot, toIn);
            this.onContentsChanged();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (this.huanghuang$canMerge(currentStack, stack)) {
            int currentAmount = StackAmountUtil.getAmount(currentStack);
            this.huanghuang$updateStackDisplay(currentStack, currentAmount + stack.getCount());
            this.onContentsChanged();
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        cir.setReturnValue(stack);
    }
}
