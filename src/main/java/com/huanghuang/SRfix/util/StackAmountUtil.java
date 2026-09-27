package com.huanghuang.SRfix.util;

import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Keeps the real count without destroying the item's original display data. */
public final class StackAmountUtil {
    public static final String AMOUNT_TAG = "YuushaAmount";
    private static final String ORIGINAL_NAME_TAG = "srfix_original_name";

    private StackAmountUtil() {}

    public static int getAmount(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(AMOUNT_TAG)) {
            return Math.max(0, stack.getTag().getInt(AMOUNT_TAG));
        }
        return stack.getCount();
    }

    public static void setCompressed(ItemStack stack, int amount) {
        if (amount <= 0) {
            stack.setCount(0);
            return;
        }

        if (amount > 1) {
            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains(AMOUNT_TAG) && !tag.contains(ORIGINAL_NAME_TAG) && stack.hasCustomHoverName()) {
                tag.putString(ORIGINAL_NAME_TAG, Component.Serializer.toJson(stack.getHoverName()));
            }
            tag.putInt(AMOUNT_TAG, amount);
            stack.setCount(1);
            stack.setHoverName(Component.literal("§e[共 " + amount + " 个] §f")
                    .append(Component.translatable(stack.getItem().getDescriptionId())));
            return;
        }

        clearCompression(stack);
        stack.setCount(1);
    }

    public static void clearCompression(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        boolean wasCompressed = tag.contains(AMOUNT_TAG);
        String originalName = tag.contains(ORIGINAL_NAME_TAG) ? tag.getString(ORIGINAL_NAME_TAG) : null;
        if (!wasCompressed && originalName == null) return;
        tag.remove(AMOUNT_TAG);
        tag.remove(ORIGINAL_NAME_TAG);

        if (originalName != null && !originalName.isEmpty()) {
            try {
                Component restored = Component.Serializer.fromJson(originalName);
                if (restored != null) stack.setHoverName(restored);
                else stack.resetHoverName();
            } catch (JsonParseException e) {
                stack.resetHoverName();
            }
        } else {
            stack.resetHoverName();
        }

        if (tag.isEmpty()) stack.setTag(null);
    }

    public static ItemStack copyForOutput(ItemStack stack) {
        ItemStack result = stack.copy();
        clearCompression(result);
        return result;
    }

    public static CompoundTag comparableTag(ItemStack stack) {
        CompoundTag tag = stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        boolean compressed = tag.contains(AMOUNT_TAG);
        String originalName = null;
        if (compressed && tag.contains(ORIGINAL_NAME_TAG)) {
            originalName = tag.getString(ORIGINAL_NAME_TAG);
        } else if (!compressed && tag.contains("display")) {
            CompoundTag display = tag.getCompound("display");
            if (display.contains("Name")) originalName = display.getString("Name");
        }
        tag.remove(AMOUNT_TAG);

        if (compressed) {
            CompoundTag display = tag.getCompound("display");
            display.remove("Name");
            if (display.isEmpty()) tag.remove("display");
        } else {
            CompoundTag display = tag.getCompound("display");
            display.remove("Name");
            if (display.isEmpty()) tag.remove("display");
        }
        tag.remove(ORIGINAL_NAME_TAG);
        if (originalName != null && !originalName.isEmpty()) tag.putString(ORIGINAL_NAME_TAG, originalName);
        return tag;
    }
}
