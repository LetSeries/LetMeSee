package com.letmesee;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 与服务端实现无关的共享逻辑：物品克隆与审计日志。
 * 只使用 Bukkit + JDK API，在 Paper 与 Spigot 上均可安全运行。
 */
public final class ContainerSnapshots {

    private ContainerSnapshots() {
    }

    public static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            copy[i] = item == null ? null : item.clone();
        }
        return copy;
    }

    public static void audit(JavaPlugin plugin, Player player, Location location,
            String blockType, String containerName) {
        plugin.getLogger().info("[审计] " + player.getName() + "(" + player.getUniqueId() + ") 查看了 "
            + describe(location) + " " + blockType + "[" + containerName + "]");
    }

    /** 只读 Location 坐标，不触碰方块，任意线程可调用。 */
    public static String describe(Location location) {
        return location.getWorld().getName()
            + " (" + location.getBlockX() + "," + location.getBlockY() + ","
            + location.getBlockZ() + ")";
    }
}
