## 🛠️ Better Summoning Rituals (srfix) - 更新与修复说明

本模组是针对 **Summoning Rituals (召唤仪式)** 1.20.1 版本的专项优化补丁。

### 1. 核心功能修复

- **解除单格 64 堆叠限制**：
  - 通过自定义 NBT 逻辑 (`YuushaAmount`)，祭坛现在的物品槽位支持**理论无限量**的堆叠（超过 64 个）。
  - 存入相同物品时会自动合并数量，并将其显示数量锁定为 1 以欺骗底层逻辑。
- **修复“吞材料”Bug**：
  - **Fix 逻辑**：在召唤检测前自动展开真实数量，召唤完成后仅扣除配方要求的数量，并将剩余材料重新压缩存回。
- **催化剂物理“粘着”修复**：
  - **彻底拦截掉落物**：修复了某些数据包将催化剂写在 `outputs` 中导致仪式结束后催化剂变为掉落物喷出的 Bug。
  - **自动补回**：如果仪式过程中催化剂被消耗，系统会瞬间将其重新“粘”回祭坛槽位，确保其永久留在祭坛上。

------

### 2. 交互逻辑优化

- **蹲下 (Shift) + 左键：切换自动模式**：
  - **操作**：玩家蹲下并左键点击祭坛。
  - **效果**：切换祭坛的“自动模式”开关。
  - **反馈**：动作栏（Action Bar）会实时显示 `[自动模式: 开启/关闭]`。开启后，只要材料集齐，仪式将自动开始。
- **蹲下 (Shift) + 右键：精准提取**：
  - 按照“先进后出”的顺序提取物品。
  - 提取出的物品会自动恢复真实的堆叠数量（如 100 个钻石提取后会变回 64+36 的形式）。
- **祭坛破坏掉落修复**：
  - 无论是被挖掘还是被炸毁，内部存储的所有超堆叠材料都会**按真实数量全部掉落**。

------

### 3. 装备过滤系统

为了防止仪式误伤装备，模组内置了 `sr_fix.json` 配置文件，其逻辑如下：

- **默认拦截**：当 `enableDefaultWeaponFilter` 为 `true` 时，系统会自动拒绝存入所有剑、斧、弓、弩、三叉戟、护甲及盾牌。
- **如何解决？**：
  1. **全局关闭**：在配置文件中将 `enableDefaultWeaponFilter` 设为 `false`。
  2. **特定解除（推荐）**：将你想作为材料的特定武器 ID 加入 `whitelist`（白名单），白名单拥有最高优先级，可无视任何拦截。

------

### 4. UI 与信息兼容 (Jade/WTHIT 兼容)

- **实时数量障眼法**：
  - **效果**：当物品存入祭坛后，名字会自动变为 **`§e[共 XX 个] §f原物品名`**。
  - **无污染性**：改名仅在物品停留于祭坛内时生效。物品离柜后改名标签会被瞬间清除。

------

## ⚙️ 配置文件示例 (`sr_fix.json`)

JSON

```
{
  "enableDefaultWeaponFilter": true,
  "blacklist": [
    "#forge:tools",
    "#goety:wands",
    "goety:dark_wand",
    "irons_spellbooks:graybeard_staff",
    "irons_spellbooks:artificer_cane",
    "irons_spellbooks:ice_staff",
    "irons_spellbooks:lightning_rod",
    "irons_spellbooks:blood_staff",
    "minecraft:nether_star"
  ],
  "whitelist": [
    "minecraft:diamond_sword" 
  ],
  "catalysts": [
    "touhou_little_maid:hakurei_gohei"
  ]
}
```

*上例：拦截所有武器和下界之星，但唯独允许“钻石剑”存入祭坛。*

------

**当前版本**：1.0.1 (Final Stable) **作者**：huanghuang
