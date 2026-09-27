package com.huanghuang.SRfix.mixin;

import com.almostreliable.summoningrituals.altar.AltarBlockEntity;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import com.almostreliable.summoningrituals.recipe.component.RecipeOutputs;
import com.almostreliable.summoningrituals.platform.PlatformBlockEntity;
import com.almostreliable.summoningrituals.Registration;
import com.huanghuang.SRfix.util.IAltarModeHolder;
import com.huanghuang.SRfix.util.StackAmountUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Optional;
import java.util.List;

@Mixin(value = AltarBlockEntity.class, priority = 10000)
public abstract class AltarBlockEntityMixin extends PlatformBlockEntity implements IAltarModeHolder {

    @Unique private static final Logger huanghuang$LOGGER = LogUtils.getLogger();
    @Unique private boolean huanghuang$isAutoMode = false;
    @Unique private ItemStack huanghuang$catalystBackup = ItemStack.EMPTY;

    @Shadow(remap = false) @Nullable private AltarRecipe currentRecipe;
    @Shadow(remap = false) private void handleSummoning(AltarRecipe recipe, @Nullable ServerPlayer player) {}
    @Shadow(remap = false) @Nullable private AltarRecipe findRecipe() { return null; }

    public AltarBlockEntityMixin(BlockEntityType<?> t, BlockPos p, BlockState s) { super(t, p, s); }

    @Override public boolean huanghuang$isAutoMode() { return this.huanghuang$isAutoMode; }
    @Override public void huanghuang$setAutoMode(boolean autoMode) { this.huanghuang$isAutoMode = autoMode; }

    @Override
    public void huanghuang$sync() {
        if (this.level != null && !this.level.isClientSide) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ==================== NBT 压缩/解压 ====================

    @Unique
    private void huanghuang$expand() {
        try {
            Container inv = this.inventory.getVanillaInv();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.hasTag()) {
                    assert stack.getTag() != null;
                    if (stack.getTag().contains(StackAmountUtil.AMOUNT_TAG)) {
                        int amount = StackAmountUtil.getAmount(stack);
                        StackAmountUtil.clearCompression(stack);
                        stack.setCount(amount);
                    }
                }
            }
        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Exception during NBT expand", e);
        }
    }

    @Unique
    private void huanghuang$compress() {
        try {
            Container inv = this.inventory.getVanillaInv();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    int realAmount = StackAmountUtil.getAmount(stack);
                    if (realAmount > 0) {
                        StackAmountUtil.setCompressed(stack, realAmount);
                    } else {
                        inv.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Exception during NBT compress", e);
        }
    }

    // ==================== 配方查找 ====================

    @Inject(method = "findRecipe", at = @At("HEAD"), remap = false)
    private void huanghuang$preFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.huanghuang$expand(); }

    @Inject(method = "findRecipe", at = @At("RETURN"), remap = false)
    private void huanghuang$postFindRecipe(CallbackInfoReturnable<AltarRecipe> cir) { this.huanghuang$compress(); }

    @Redirect(method = "findRecipe", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<AltarRecipe> huanghuang$compressAfterRecipeLookup(RecipeManager manager,
            RecipeType<AltarRecipe> recipeType, Container inventory, Level level) {
        try {
            return (Optional<AltarRecipe>) (Optional<?>) manager.getRecipeFor((RecipeType) recipeType, inventory, level);
        } finally {
            this.huanghuang$compress();
        }
    }

    // ==================== tick 流水线 ====================

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;handleRecipe(Lcom/almostreliable/summoningrituals/recipe/AltarRecipe;)Z"),
            remap = false)
    private void huanghuang$preHandleRecipe(CallbackInfo ci) {
        this.huanghuang$expand();
        // 备份催化剂（AltarInventoryMixin 内部也有保护，这里做第二道防线）
        this.huanghuang$catalystBackup = this.inventory.getCatalyst().copy();
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/recipe/component/RecipeOutputs;handleRecipe(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V"),
            remap = false
    )
    private void huanghuang$redirectHandleRecipeOutputs(RecipeOutputs outputs, ServerLevel serverLevel, BlockPos blockPos) {
        try {
            // ① 产出物生成前：确保催化剂在槽位里（兜底恢复）
            if (!this.huanghuang$catalystBackup.isEmpty() && this.inventory.getCatalyst().isEmpty()) {
                this.inventory.setCatalyst(this.huanghuang$catalystBackup.copy());
            }

            // ② 记录已有实体后调用产出逻辑，只清理本次新生成的同类催化剂实体。
            if (!this.huanghuang$catalystBackup.isEmpty()) {
                net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(blockPos).inflate(2.0);
                Set<ItemEntity> existingItems = Collections.newSetFromMap(new IdentityHashMap<>());
                existingItems.addAll(serverLevel.getEntitiesOfClass(ItemEntity.class, area));
                outputs.handleRecipe(serverLevel, blockPos);
                for (ItemEntity itemEntity : serverLevel.getEntitiesOfClass(ItemEntity.class, area)) {
                    if (!existingItems.contains(itemEntity)
                            && ItemStack.isSameItemSameTags(itemEntity.getItem(), this.huanghuang$catalystBackup)) {
                        itemEntity.discard();
                    }
                }
            } else {
                outputs.handleRecipe(serverLevel, blockPos);
            }

        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Failed handling recipe outputs", e);
        } finally {
            this.huanghuang$compress();
            this.huanghuang$catalystBackup = ItemStack.EMPTY;
        }
    }

    // ==================== 手动/自动模式控制 ====================

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;getCatalyst()Lnet/minecraft/world/item/ItemStack;"),
            remap = false
    )
    private ItemStack huanghuang$controlAutoTick(com.almostreliable.summoningrituals.inventory.AltarInventory inventory) {
        // 手动模式：隐藏催化剂，阻止 tick 自动触发召唤
        if (!this.huanghuang$isAutoMode) {
            return ItemStack.EMPTY;
        }
        return inventory.getCatalyst();
    }

    // 自动模式下，resetSummoning(true) 不弹出任何物品
    @Redirect(
            method = "resetSummoning",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;popLastInserted()V"),
            remap = false
    )
    private void huanghuang$redirectPopInReset(com.almostreliable.summoningrituals.inventory.AltarInventory inventory) {
        if (!this.huanghuang$isAutoMode) {
            inventory.popLastInserted();
        }
    }

    // 自动模式下，handleSummoning 失败不弹出任何物品
    @Redirect(
            method = "handleSummoning",
            at = @At(value = "INVOKE",
                    target = "Lcom/almostreliable/summoningrituals/inventory/AltarInventory;popLastInserted()V"),
            remap = false
    )
    private void huanghuang$redirectPopInSummoning(com.almostreliable.summoningrituals.inventory.AltarInventory inventory) {
        if (!this.huanghuang$isAutoMode) {
            inventory.popLastInserted();
        }
    }

    // ==================== 物品合并 ====================

    @Unique
    private boolean huanghuang$canMerge(ItemStack inSlot, ItemStack incoming) {
        if (inSlot.isEmpty() || incoming.isEmpty()) return false;
        if (inSlot.getItem() != incoming.getItem()) return false;
        return StackAmountUtil.comparableTag(inSlot).equals(StackAmountUtil.comparableTag(incoming));
    }

    // ==================== 辅助：弹出槽位到世界 ====================

    @Unique
    private void huanghuang$extractSlotToWorld(Container inv, int slot, Level level, BlockPos pos) {
        ItemStack inSlot = inv.getItem(slot);
        int realCount = StackAmountUtil.getAmount(inSlot);
        ItemStack result = StackAmountUtil.copyForOutput(inSlot);
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

    /**
     * A catalyst item can also be a normal input for another recipe. Once a
     * catalyst is already selected, use the recipes for that catalyst to
     * decide whether a blacklisted item is a valid material instead of
     * treating the global catalyst cache as a permanent item classification.
     */
    @Unique
    private boolean huanghuang$isRecipeMaterial(ItemStack stack) {
        if (stack.isEmpty() || this.level == null) return false;

        ItemStack selectedCatalyst = this.inventory.getCatalyst();
        if (selectedCatalyst.isEmpty()) return false;

        try {
            RecipeType<AltarRecipe> type = Registration.ALTAR_RECIPE.type().get();
            List<AltarRecipe> recipes = this.level.getRecipeManager().getAllRecipesFor(type);
            for (AltarRecipe recipe : recipes) {
                if (!recipe.getCatalyst().test(selectedCatalyst)) continue;
                if (recipe.getInputs().stream().anyMatch(input -> input.ingredient().test(stack))) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            huanghuang$LOGGER.warn("[SRfix] Failed checking recipe material for {}", stack, e);
        }
        return false;
    }

    // ==================== 玩家交互（插入/抽取） ====================

    @Inject(method = "handleInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void huanghuang$onHandleInteraction(@Nullable ServerPlayer player, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (this.progress > 0 || player == null) return;
            Container inv = this.inventory.getVanillaInv();

            // ---- 空手 ----
            if (stack.isEmpty()) {
                // Shift + 右键：交给 popLastInserted（由 AltarInventoryMixin 控制提取顺序）
                if (player.isShiftKeyDown()) {
                    this.inventory.popLastInserted();
                    this.huanghuang$sync();
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                // 右键空手（非 Shift）：手动模式下触发召唤
                else if (!this.huanghuang$isAutoMode) {
                    AltarRecipe recipe = this.findRecipe();
                    if (recipe != null) {
                        this.handleSummoning(recipe, player);
                        this.huanghuang$sync();
                    }
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
            }
            // ---- 手持物品 ----
            else {
                // 配方催化剂优先于普通物品过滤，避免 #forge:tools 等规则拦截合法催化剂。
                // Catalyst candidates are only routed to the catalyst slot
                // while that slot is empty. If a catalyst is already selected,
                // the same item may legitimately be a normal input for the
                // selected recipe (or another recipe using that catalyst).
                boolean isCatalyst = this.inventory.getCatalyst().isEmpty()
                        && (com.huanghuang.SRfix.util.SRfixConfig.isConfiguredCatalyst(stack)
                        || AltarRecipe.CATALYST_CACHE.stream().anyMatch(ing -> ing.test(stack)));
                if (isCatalyst) {
                    if (!this.inventory.getCatalyst().isEmpty()) {
                        cir.setReturnValue(stack);
                        return;
                    }
                    this.inventory.setCatalyst(stack.copyWithCount(1));
                    this.huanghuang$compress();
                    if (this.huanghuang$isAutoMode) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) this.handleSummoning(recipe, player);
                    }
                    this.huanghuang$sync();
                    ItemStack rem = stack.copy();
                    rem.shrink(1);
                    cir.setReturnValue(rem);
                    return;
                }

                // 黑名单物品：当作空手处理（提取/召唤）
                boolean recipeMaterial = huanghuang$isRecipeMaterial(stack);
                if (!recipeMaterial && !com.huanghuang.SRfix.util.SRfixConfig.isAllowed(stack)) {
                    if (player.isShiftKeyDown()) {
                        this.inventory.popLastInserted();
                        this.huanghuang$sync();
                    } else if (!this.huanghuang$isAutoMode) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) {
                            this.handleSummoning(recipe, player);
                            this.huanghuang$sync();
                        }
                    }
                    cir.setReturnValue(stack);
                    return;
                }

                // 普通材料：尝试合并到已有槽位
                boolean merged = false;
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack inSlot = inv.getItem(i);
                    if (huanghuang$canMerge(inSlot, stack)) {
                        int current = StackAmountUtil.getAmount(inSlot);
                        StackAmountUtil.setCompressed(inSlot, current + stack.getCount());
                        merged = true;
                        break;
                    }
                }

                // 无法合并则放入空槽位
                if (!merged) {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (inv.getItem(i).isEmpty()) {
                            ItemStack toIn = stack.copy();
                            StackAmountUtil.setCompressed(toIn, stack.getCount());
                            inv.setItem(i, toIn);
                            merged = true;
                            break;
                        }
                    }
                }

                if (merged) {
                    this.huanghuang$compress();
                    this.huanghuang$sync();
                    if (this.huanghuang$isAutoMode && !this.inventory.getCatalyst().isEmpty()) {
                        AltarRecipe recipe = this.findRecipe();
                        if (recipe != null) this.handleSummoning(recipe, player);
                    }
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Failed onHandleInteraction", e);
            cir.setReturnValue(stack);
        }
    }

    // ==================== 祭坛破坏 ====================

    @Inject(method = "playerDestroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void huanghuang$onPlayerDestroy(boolean creative, CallbackInfo ci) {
        try {
            if (this.level == null || this.level.isClientSide) return;
            ci.cancel();
            Container inv = this.inventory.getVanillaInv();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    int amount = StackAmountUtil.getAmount(stack);
                    ItemStack base = StackAmountUtil.copyForOutput(stack);

                    inv.setItem(i, ItemStack.EMPTY); // 先清槽位，杜绝底层二次掉落

                    while (amount > 0) {
                        int count = Math.min(amount, base.getMaxStackSize());
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
        } catch (Exception e) {
            huanghuang$LOGGER.error("[SRfix] Failed onPlayerDestroy", e);
        }
    }

    // ==================== 存档 ====================

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void huanghuang$onSave(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("YuushaAutoMode", this.huanghuang$isAutoMode);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void huanghuang$onLoad(CompoundTag tag, CallbackInfo ci) {
        this.huanghuang$isAutoMode = tag.getBoolean("YuushaAutoMode");
    }
}
