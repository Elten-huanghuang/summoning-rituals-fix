package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.altar.AltarBlockEntity;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.almostreliable.summoningrituals.recipe.component.RecipeOutputs;
import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.almostreliable.summoningrituals.Registration;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
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

    @Unique private static final Logger yuusha$LOGGER = LogUtils.getLogger();
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

    // ==================== NBT 压缩/解压 ====================

    @Unique
    private void yuusha$expand() {
        try {
            Container inv = this.inventory.getVanillaInv();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.hasTag()) {
                    assert stack.getTag() != null;
                    if (stack.getTag().contains("YuushaAmount")) {
                        stack.setCount(stack.getTag().getInt("YuushaAmount"));
                        stack.getTag().remove("YuushaAmount");
                        if (stack.getTag().isEmpty()) stack.setTag(null);
                        stack.resetHoverName();
                    }
                }
            }
        } catch (Exception e) {
            yuusha$LOGGER.error("[SRfix] Exception during NBT expand", e);
        }
    }

    @Unique
    private void yuusha$compress() {
        try {
            Container inv = this.inventory.getVanillaInv();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    int realAmount = stack.getCount();
                    if (stack.hasTag() && stack.getTag().contains("YuushaAmount")) {
                        realAmount = stack.getTag().getInt("YuushaAmount");
                    }

                    if (realAmount > 1) {
                        stack.getOrCreateTag().putInt("YuushaAmount", realAmount);
                        stack.setCount(1);
                        net.minecraft.network.chat.MutableComponent customName = net.minecraft.network.chat.Component.literal("§e[共 " + realAmount + " 个] §f")
                                .append(net.minecraft.network.chat.Component.translatable(stack.getItem().getDescriptionId()));
                        stack.setHoverName(customName);
                    } else if (realAmount == 1) {
                        if (stack.hasTag()) {
                            stack.getTag().remove("YuushaAmount");
                            if (stack.getTag().isEmpty()) stack.setTag(null);
                        }
                        stack.resetHoverName();
                        stack.setCount(1);
                    } else {
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception e) {
            yuusha$LOGGER.error("[SRfix] Exception during NBT compress", e);
        }
    }

    // ==================== 配方查找 ====================

    @Inject(method = "findRecipe", at = @At("HEAD"), remap = false)
    private void yuusha$preFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.yuusha$expand(); }

    @Inject(method = "findRecipe", at = @At("RETURN"), remap = false)
    private void yuusha$postFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.yuusha$compress(); }

    // ==================== tick 流水线 ====================

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;handleRecipe(Lcom/almostreliable/summoningrituals/recipe/AltarRecipe;)Z"),
            remap = false)
    private void yuusha$preHandleRecipe(CallbackInfo ci) {
        this.yuusha$expand();
        // 备份催化剂（AltarInventoryMixin 内部也有保护，这里做第二道防线）
        this.yuusha$catalystBackup = this.inventory.getCatalyst().copy();
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/recipe/component/RecipeOutputs;handleRecipe(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V"),
            remap = false
    )
    private void yuusha$redirectHandleRecipeOutputs(RecipeOutputs outputs, ServerLevel serverLevel, BlockPos blockPos) {
        try {
            // ① 产出物生成前：确保催化剂在槽位里（兜底恢复）
            if (!this.yuusha$catalystBackup.isEmpty() && this.inventory.getCatalyst().isEmpty()) {
                this.inventory.setCatalyst(this.yuusha$catalystBackup.copy());
            }

            // ② 调用原版产出逻辑
            outputs.handleRecipe(serverLevel, blockPos);

            // ③ 立刻扫描并清除被弹飞的催化剂掉落物（无 tickCount 限制）
            if (!this.yuusha$catalystBackup.isEmpty()) {
                net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(blockPos).inflate(5.0);
                List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class, area);
                for (ItemEntity itemEntity : items) {
                    if (itemEntity.getItem().getItem() == this.yuusha$catalystBackup.getItem()) {
                        itemEntity.discard();
                    }
                }
            }

            // ④ 压缩物品显示
            this.yuusha$compress();
            this.yuusha$catalystBackup = ItemStack.EMPTY;
        } catch (Exception e) {
            yuusha$LOGGER.error("[SRfix] Failed handling recipe outputs", e);
        }
    }

    // ==================== 手动/自动模式控制 ====================

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;getCatalyst()Lnet/minecraft/world/item/ItemStack;"),
            remap = false
    )
    private ItemStack yuusha$controlAutoTick(com.almostreliable.summoningrituals.inventory.AltarInventory inventory) {
        // 手动模式：隐藏催化剂，阻止 tick 自动触发召唤
        if (!this.yuusha$isAutoMode) {
            return ItemStack.EMPTY;
        }
        return inventory.getCatalyst();
    }

    // ==================== 物品合并 ====================

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

    // ==================== 辅助：弹出槽位到世界 ====================

    @Unique
    private void yuusha$extractSlotToWorld(Container inv, int slot, Level level, BlockPos pos) {
        ItemStack inSlot = inv.getItem(slot);
        int realCount = (inSlot.hasTag() && inSlot.getTag().contains("YuushaAmount"))
                ? inSlot.getTag().getInt("YuushaAmount") : inSlot.getCount();

        ItemStack result = inSlot.copy();
        if (result.hasTag()) {
            result.getTag().remove("YuushaAmount");
            if (result.getTag().isEmpty()) result.setTag(null);
        }
        result.resetHoverName();
        inv.setItem(slot, ItemStack.EMPTY);

        while (realCount > 0) {
            int toGive = Math.min(realCount, result.getMaxStackSize());
            ItemStack drop = result.copyWithCount(toGive);
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, drop);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
            realCount -= toGive;
        }
    }

    // ==================== 玩家交互（插入/抽取） ====================

    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void yuusha$onHandleInteraction(@Nullable ServerPlayer player, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (this.progress > 0 || player == null) return;
            Container inv = this.inventory.getVanillaInv();

            // ---- 空手 ----
            if (stack.isEmpty()) {
                // Shift + 右键：交给 popLastInserted（由 AltarInventoryMixin 控制提取顺序）
                if (player.isShiftKeyDown()) {
                    this.inventory.popLastInserted();
                    this.yuusha$sync();
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                // 右键空手（非 Shift）：手动模式下触发召唤
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
            // ---- 手持物品 ----
            else {
                // 黑名单物品：当作空手处理（提取/召唤）
                if (!com.huanghuang.SRfix.util.SRfixConfig.isAllowed(stack)) {
                    if (player.isShiftKeyDown()) {
                        this.inventory.popLastInserted();
                        this.yuusha$sync();
                    } else if (!this.yuusha$isAutoMode) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) {
                            this.handleSummoning(recipe, player);
                            this.yuusha$sync();
                        }
                    }
                    cir.setReturnValue(stack);
                    return;
                }

                // 催化剂
                boolean isCatalyst = AltarRecipe.CATALYST_CACHE.stream().anyMatch(ing -> ing.test(stack));
                if (isCatalyst) {
                    this.inventory.setCatalyst(stack.copyWithCount(1));
                    this.yuusha$compress();
                    if (this.yuusha$isAutoMode) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) this.handleSummoning(recipe, player);
                    }
                    this.yuusha$sync();
                    ItemStack rem = stack.copy(); rem.shrink(1);
                    cir.setReturnValue(rem);
                    return;
                }

                // 普通材料：尝试合并到已有槽位
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

                // 无法合并则放入空槽位
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
                    this.yuusha$compress();
                    this.yuusha$sync();
                    if (this.yuusha$isAutoMode && !this.inventory.getCatalyst().isEmpty()) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) this.handleSummoning(recipe, player);
                    }
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            yuusha$LOGGER.error("[SRfix] Failed onHandleInteraction", e);
        }
    }

    // ==================== 祭坛破坏 ====================

    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void yuusha$onPlayerDestroy(boolean creative, CallbackInfo ci) {
        try {
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

                    inv.setItem(i, ItemStack.EMPTY); // 先清槽位，杜绝底层二次掉落

                    while (amount > 0) {
                        int count = Math.min(amount, 64);
                        this.level.addFreshEntity(new ItemEntity(level,
                                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                                base.copyWithCount(count)));
                        amount -= count;
                    }
                }
            }
            if (!this.inventory.getCatalyst().isEmpty()) {
                this.level.addFreshEntity(new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                        this.inventory.getCatalyst().copy()));
                this.inventory.setCatalyst(ItemStack.EMPTY);
            }
            if (!creative) {
                this.level.addFreshEntity(new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                        new ItemStack((ItemLike) Registration.ALTAR_ITEM.get())));
            }
            ci.cancel();
        } catch (Exception e) {
            yuusha$LOGGER.error("[SRfix] Failed onPlayerDestroy", e);
        }
    }

    // ==================== 存档 ====================

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void yuusha$onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("YuushaAutoMode", this.yuusha$isAutoMode);
        tag.remove("permission_to_damage");
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void yuusha$onLoad(CompoundTag tag, CallbackInfo ci) {
        this.yuusha$isAutoMode = tag.getBoolean("YuushaAutoMode");
        tag.remove("permission_to_damage");
    }
}