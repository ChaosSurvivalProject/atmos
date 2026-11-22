package dev.atmos.server.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** 庇护判定：头顶有遮挡 + 室内 = 安全区。只惩罚暴露实体与作物。 */
public final class Shelter {

    /** 玩家是否处于庇护中（头顶 1~3 格有实心方块，且非露天） */
    public static boolean isSheltered(Location loc) {
        World w = loc.getWorld();
        if (w == null) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (int dy = 1; dy <= 3; dy++) {
            Block b = w.getBlockAt(x, y + dy, z);
            Material m = b.getType();
            if (m.isSolid() && !m.name().contains("GLASS") && !m.name().contains("LEAVES")) {
                return true;
            }
        }
        return false;
    }
}
