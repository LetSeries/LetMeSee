package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spigot / CraftBukkit 同步路径：打开只读视图并按配置自动刷新。
 * 单线程执行，无跨线程操作。只引用 Bukkit + JDK，可安全加载。
 */
public final class LegacyRefresher {

    private LegacyRefresher() {
    }

    /**
     * 同步读取容器并打开只读视图，注册刷新会话。
     * 读取失败时直接提示玩家，不打开界面。
     */
    public static void open(JavaPlugin plugin, Player player, Location targetLocation,
            boolean auditEnabled, int refreshTicks) {
        ContainerSnapshots.ReadResult result = ContainerSnapshots.read(targetLocation);
        if (result.status() != ContainerSnapshots.ReadStatus.OK) {
            player.sendMessage(ContainerSnapshots.messageFor(result.status()));
            return;
        }

        String containerName = ContainerSnapshots.truncate(result.fullName(), 32);
        Inventory viewInv = result.type() == org.bukkit.event.inventory.InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), result.size(),
                "§7[只读] " + containerName)
            : Bukkit.createInventory(new ReadOnlyHolder(), result.type(),
                "§7[只读] " + containerName);
        viewInv.setContents(result.contents());

        ContainerSnapshots.audit(plugin, player, targetLocation, result.blockType(),
            result.fullName(), auditEnabled);

        player.openInventory(viewInv);
        player.sendMessage("§a已打开 " + containerName + " 的只读视图 §7"
            + ContainerSnapshots.describe(targetLocation));

        if (refreshTicks <= 0) {
            return;
        }
        ViewSession session = ViewSession.create(targetLocation, viewInv, null);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ViewSession current = ViewSession.get(player.getUniqueId());
            if (current == null || current.view() != viewInv) {
                holder[0].cancel();
                return;
            }
            ContainerSnapshots.ReadResult fresh = ContainerSnapshots.read(targetLocation);
            if (!ViewSession.applyRefresh(player, current, fresh)) {
                holder[0].cancel();
            }
        }, refreshTicks, refreshTicks);
        ViewSession.register(player, session.withCanceller(() -> holder[0].cancel()));
    }

    /**
     * 同步快照目标玩家库存并打开只读视图，注册刷新会话。
     * 目标离线直接提示，不打开界面。
     */
    public static void openPlayerView(JavaPlugin plugin, Player viewer, Player target,
            boolean enderChest, boolean auditEnabled, int refreshTicks) {
        PlayerViews.Snapshot snapshot = PlayerViews.snapshot(target, enderChest);
        Inventory viewInv = PlayerViews.openSnapshot(plugin, viewer, snapshot, auditEnabled);
        if (refreshTicks <= 0) {
            return;
        }
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ViewSession current = ViewSession.get(viewer.getUniqueId());
            if (current == null || current.view() != viewInv) {
                holder[0].cancel();
                return;
            }
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online == null || !online.isOnline()) {
                viewer.closeInventory();
                viewer.sendMessage("§e目标玩家已下线，视图已关闭");
                ViewSession.close(viewer.getUniqueId());
                holder[0].cancel();
                return;
            }
            PlayerViews.Snapshot fresh;
            try {
                fresh = PlayerViews.snapshot(online, enderChest);
            } catch (Exception e) {
                viewer.closeInventory();
                viewer.sendMessage("§e无法读取目标库存，视图已关闭");
                ViewSession.close(viewer.getUniqueId());
                holder[0].cancel();
                return;
            }
            if (!ViewSession.applyPlayerRefresh(viewer, current, fresh)) {
                holder[0].cancel();
            }
        }, refreshTicks, refreshTicks);
        ViewSession.register(viewer,
            ViewSession.createForPlayer(viewInv, target.getUniqueId())
                .withCanceller(() -> holder[0].cancel()));
    }
}
