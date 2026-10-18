package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Spigot / CraftBukkit / Paper 同步路径：打开只读视图并按配置自动刷新。
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
            boolean auditEnabled, boolean auditFileEnabled, int refreshTicks) {
        ContainerSnapshots.ReadResult result = ContainerSnapshots.read(targetLocation);
        if (result.status() != ContainerSnapshots.ReadStatus.OK) {
            player.sendMessage(ContainerSnapshots.messageFor(result.status()));
            return;
        }

        String containerName = ReadOnlyViews.body(result.fullName());
        Inventory viewInv = ReadOnlyViews.create(result.type(), result.size(),
            result.fullName());
        viewInv.setContents(result.contents());

        ContainerSnapshots.audit(plugin, player, targetLocation, result.blockType(),
            result.fullName(), auditEnabled, auditFileEnabled);

        player.openInventory(viewInv);
        player.sendMessage("§a已打开 " + containerName + " 的只读视图 §7"
            + ContainerSnapshots.describe(targetLocation));

        // 注册必须在 openInventory 之后：打开新界面会先为旧界面触发 CloseEvent，
        // 若此时新会话已入表，反而会被旧界面的关闭事件清掉。
        ViewSession.register(player,
            ViewSession.create(targetLocation, viewInv)
                .withCanceller(refresh(plugin, player, targetLocation, viewInv, refreshTicks)));
    }

    /**
     * 启动定时刷新并返回取消器。刷新间隔为 0 时返回空实现，
     * 但会话仍然注册——关闭界面、退出游戏、插件卸载时能统一清理。
     */
    private static Runnable refresh(JavaPlugin plugin, Player player,
            Location targetLocation, Inventory viewInv, int refreshTicks) {
        if (refreshTicks <= 0) {
            return () -> {
            };
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ViewSession current = ViewSession.get(player.getUniqueId());
            BukkitTask self = ref.get();
            if (current == null || current.view() != viewInv) {
                cancelQuietly(self);
                return;
            }
            ContainerSnapshots.ReadResult fresh = ContainerSnapshots.read(targetLocation);
            if (!ViewSession.applyRefresh(player, current, fresh)) {
                cancelQuietly(self);
            }
        }, refreshTicks, refreshTicks));
        return () -> cancelQuietly(ref.get());
    }

    /** 静默取消任务，null 安全，卸载期失败可忽略。 */
    private static void cancelQuietly(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
                // 卸载期取消失败可忽略
            }
        }
    }

    /**
     * 同步快照目标玩家库存并打开只读视图，注册刷新会话。
     * 目标离线直接提示，不打开界面。
     */
    public static void openPlayerView(JavaPlugin plugin, Player viewer, Player target,
            boolean enderChest, boolean auditEnabled, boolean auditFileEnabled,
            int refreshTicks) {
        PlayerViews.Snapshot snapshot = PlayerViews.snapshot(target, enderChest);
        Inventory viewInv = PlayerViews.openSnapshot(plugin, viewer, snapshot,
            auditEnabled, auditFileEnabled);

        ViewSession.register(viewer,
            ViewSession.createForPlayer(viewInv, target.getUniqueId())
                .withCanceller(refreshPlayer(plugin, viewer, target, enderChest,
                    viewInv, refreshTicks)));
    }

    /** 玩家库存的定时刷新；间隔为 0 时返回空取消器，会话照常注册。 */
    private static Runnable refreshPlayer(JavaPlugin plugin, Player viewer, Player target,
            boolean enderChest, Inventory viewInv, int refreshTicks) {
        if (refreshTicks <= 0) {
            return () -> {
            };
        }
        AtomicReference<BukkitTask> ref = new AtomicReference<>();
        ref.set(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ViewSession current = ViewSession.get(viewer.getUniqueId());
            BukkitTask self = ref.get();
            if (current == null || current.view() != viewInv) {
                cancelQuietly(self);
                return;
            }
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online == null || !online.isOnline()) {
                viewer.closeInventory();
                viewer.sendMessage("§e目标玩家已下线，视图已关闭");
                ViewSession.close(viewer.getUniqueId());
                cancelQuietly(self);
                return;
            }
            PlayerViews.Snapshot fresh;
            try {
                fresh = PlayerViews.snapshot(online, enderChest);
            } catch (Exception e) {
                viewer.closeInventory();
                viewer.sendMessage("§e无法读取目标库存，视图已关闭");
                ViewSession.close(viewer.getUniqueId());
                cancelQuietly(self);
                return;
            }
            if (!ViewSession.applyPlayerRefresh(viewer, current, fresh)) {
                cancelQuietly(self);
            }
        }, refreshTicks, refreshTicks));
        return () -> cancelQuietly(ref.get());
    }
}
