package com.huanghuang.SRfix.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class SRfixConfig {
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("sr_fix.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 默认开启硬编码过滤（拦截所有的剑、斧、防具、弓箭、三叉戟、盾牌）
    public static boolean enableDefaultWeaponFilter = true;
    public static List<String> blacklist = new ArrayList<>();
    public static List<String> whitelist = new ArrayList<>();
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        if (!CONFIG_FILE.exists()) {
            // 初始化生成样例配置
            blacklist.add("#forge:tools"); // 拦截 forge:tools 标签下的所有物品
            whitelist.add("minecraft:diamond_sword"); // 白名单放行钻石剑
            save();
            loaded = true;
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json.has("enableDefaultWeaponFilter")) enableDefaultWeaponFilter = json.get("enableDefaultWeaponFilter").getAsBoolean();

            blacklist.clear();
            if (json.has("blacklist")) json.getAsJsonArray("blacklist").forEach(e -> blacklist.add(e.getAsString()));

            whitelist.clear();
            if (json.has("whitelist")) json.getAsJsonArray("whitelist").forEach(e -> whitelist.add(e.getAsString()));
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonObject json = new JsonObject();
            json.addProperty("enableDefaultWeaponFilter", enableDefaultWeaponFilter);

            JsonArray bl = new JsonArray();
            blacklist.forEach(bl::add);
            json.add("blacklist", bl);

            JsonArray wl = new JsonArray();
            whitelist.forEach(wl::add);
            json.add("whitelist", wl);

            GSON.toJson(json, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isAllowed(ItemStack stack) {
        load();
        if (stack.isEmpty()) return true;
        Item item = stack.getItem();
        ResourceLocation regName = ForgeRegistries.ITEMS.getKey(item);
        if (regName == null) return true;

        String name = regName.toString();

        // 1. 白名单最高优先级（如果是白名单物品，直接放行）
        if (matchesList(stack, name, whitelist)) return true;

        // 2. 黑名单拦截（检查具体物品 ID 或 标签）
        if (matchesList(stack, name, blacklist)) return false;

        // 3. 默认类拦截（精准识别：刀剑、斧头、防具、弓、弩、三叉戟、盾牌）
        if (enableDefaultWeaponFilter) {
            if (item instanceof SwordItem ||
                    item instanceof AxeItem ||
                    item instanceof ArmorItem ||
                    item instanceof TridentItem ||
                    item instanceof BowItem ||
                    item instanceof CrossbowItem ||
                    item instanceof ShieldItem) {
                return false;
            }
        }
        return true; // 既不在黑名单也不是武器，默认放行
    }

    // 把原来的 @SuppressWarnings("deprecation") 改成下面这行：
    @SuppressWarnings({"deprecation", "removal"})
    private static boolean matchesList(ItemStack stack, String name, List<String> list) {
        for (String entry : list) {
            if (entry.startsWith("#")) {
                // 标签检测 (例如 "#minecraft:swords")
                // 1.20.1 必须使用 new ResourceLocation，否则会找不到方法而崩溃！
                ResourceLocation tagLoc = new ResourceLocation(entry.substring(1));
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                if (stack.is(tagKey)) return true;
            } else {
                // 物品 ID 检测 (例如 "minecraft:stick")
                if (name.equals(entry)) return true;
            }
        }
        return false;
    }
}