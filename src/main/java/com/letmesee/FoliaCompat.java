package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
 * 绝不能直接引用本类（包括 {@code FoliaCompat.isSupported()} 这类静态调用）。</p>
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
     * @param displayName 预先在调用线程采集的容器展示名
     * @param opener      在玩家线程打开视图的回调，收到已建好的只读视图
     */
    public static void openContainer(JavaPlugin plugin, Player player, Location targetLocation,
            String displayName, ServerCompat.FoliaOpener opener) {
        Bukkit.getRegionScheduler().run(plugin, targetLocation, task -> {
            Block block = targetLocation.getBlock();
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
                ? Component.text(displayName)
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
        });
    }

    private static void runOnPlayer(JavaPlugin plugin, Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.run();
            }
        }, null);
    }
}
