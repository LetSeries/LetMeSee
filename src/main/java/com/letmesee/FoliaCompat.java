package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Folia / Paper 专属逻辑隔离层。
 *
 * <p>本类直接引用 {@code Bukkit.getRegionScheduler()}、{@code Player.getScheduler()}
 * 与 Adventure API，在 Spigot / CraftBukkit 上这些符号不存在，
 * <b>连类加载都会失败</b>。调用方只能经 {@code ServerCompat} 反射进入，
 * 绝不能直接引用本类（包括静态调用）。进入本类后的一切方块触碰都发生在
 * 目标区域线程内，调用线程保持零触碰。</p>
 */
public final class FoliaCompat {

    private FoliaCompat() {
    }

    /**
     * 在目标区域线程读取容器并克隆，组装只读视图后切回玩家线程打开。
     *
     * <p>公开方法签名只含 Bukkit / JDK 类型，避免调用方加载期被 Adventure
     * 方法签名污染；Adventure 只出现在方法体内部。</p>
     *
     * @param opener 在玩家线程打开视图的回调，收到已建好的只读视图
     */
    public static void openContainer(JavaPlugin plugin, Player player, Location targetLocation,
            ServerCompat.FoliaOpener opener) {
        Bukkit.getRegionScheduler().run(plugin, targetLocation, task -> {
            try {
                readAndOpen(plugin, player, targetLocation, opener);
            } catch (Exception e) {
                plugin.getLogger().warning("[LetMeSee] 读取容器失败 "
                    + describe(targetLocation) + ": " + e);
                runOnPlayer(plugin, player, () ->
                    player.sendMessage("§c读取容器失败，请稍后重试"));
            }
        });
    }

    private static void readAndOpen(JavaPlugin plugin, Player player, Location targetLocation,
            ServerCompat.FoliaOpener opener) {
        Block block = targetLocation.getBlock();

        if (block.getType() == Material.ENDER_CHEST) {
            runOnPlayer(plugin, player, () -> player.sendMessage(
                "§c末影箱是玩家私有背包，不支持只读查看"));
            return;
        }

        BlockState state = block.getState();

        if (!(state instanceof BlockInventoryHolder holder)) {
            runOnPlayer(plugin, player, () ->
                player.sendMessage("§c该位置没有容器"));
            return;
        }

        Inventory targetInv = holder.getInventory();
        if (targetInv == null) {
            runOnPlayer(plugin, player, () ->
                player.sendMessage("§c无法读取该容器的物品"));
            return;
        }
        ItemStack[] contents = ContainerSnapshots.cloneContents(targetInv.getContents());

        Component customName = state instanceof Nameable nameable ? nameable.customName() : null;
        Component body = customName == null || Component.empty().equals(customName)
            ? Component.text(ContainerNames.displayName(block.getType()))
            : customName;
        Component title = Component.text("[只读] ", NamedTextColor.GRAY).append(body);
        String plainName = PlainTextComponentSerializer.plainText().serialize(body);

        InventoryType type = targetInv.getType();
        Inventory viewInv = type == InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), targetInv.getSize(), title)
            : Bukkit.createInventory(new ReadOnlyHolder(), type, title);
        viewInv.setContents(contents);

        ContainerSnapshots.audit(plugin, player, targetLocation, block.getType().name(), plainName);

        runOnPlayer(plugin, player, () -> opener.open(viewInv, plainName));
    }

    /** 只读 Location 坐标，不触碰方块，任意线程可调用。 */
    private static String describe(Location location) {
        return location.getWorld().getName()
            + " (" + location.getBlockX() + "," + location.getBlockY() + ","
            + location.getBlockZ() + ")";
    }

    private static void runOnPlayer(JavaPlugin plugin, Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.run();
            }
        }, null);
    }
}
