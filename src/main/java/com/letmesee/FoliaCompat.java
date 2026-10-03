package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.atomic.AtomicReference;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

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
     * 在目标区域线程读取容器并克隆，组装只读视图后切回玩家线程打开，
     * 并按配置注册自动刷新。
     *
     * <p>公开方法签名只含 Bukkit / JDK 类型，避免调用方加载期被 Adventure
     * 方法签名污染；Adventure 只出现在方法体内部。</p>
     *
     * @param opener 在玩家线程打开视图的回调，收到已建好的只读视图
     */
    public static void openContainer(JavaPlugin plugin, Player player, Location targetLocation,
            boolean auditEnabled, int refreshTicks, ServerCompat.FoliaOpener opener) {
        Bukkit.getRegionScheduler().run(plugin, targetLocation, task -> {
            try {
                readAndOpen(plugin, player, targetLocation, auditEnabled, refreshTicks, opener);
            } catch (Exception e) {
                plugin.getLogger().warning("[LetMeSee] 读取容器失败 "
                    + ContainerSnapshots.describe(targetLocation) + ": " + e);
                runOnPlayer(plugin, player, () ->
                    player.sendMessage("§c读取容器失败，请稍后重试"));
            }
        });
    }

    private static void readAndOpen(JavaPlugin plugin, Player player, Location targetLocation,
            boolean auditEnabled, int refreshTicks, ServerCompat.FoliaOpener opener) {
        ContainerSnapshots.ReadResult result = ContainerSnapshots.read(targetLocation);
        if (result.status() != ContainerSnapshots.ReadStatus.OK) {
            String message = ContainerSnapshots.messageFor(result.status());
            runOnPlayer(plugin, player, () -> player.sendMessage(message));
            return;
        }

        String containerName = ContainerSnapshots.truncate(result.fullName(), 32);
        Component title = Component.text("[只读] ", NamedTextColor.GRAY)
            .append(Component.text(containerName));

        InventoryType type = result.type();
        Inventory viewInv = type == InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), result.size(), title)
            : Bukkit.createInventory(new ReadOnlyHolder(), type, title);
        viewInv.setContents(result.contents());

        ContainerSnapshots.audit(plugin, player, targetLocation, result.blockType(),
            result.fullName(), auditEnabled);

        String plainName = containerName;
        String where = ContainerSnapshots.describe(targetLocation);
        runOnPlayer(plugin, player, () -> {
            opener.open(viewInv, plainName, where);
            if (refreshTicks > 0) {
                scheduleRefresh(plugin, player, targetLocation, refreshTicks, viewInv);
            }
        });
    }

    /**
     * 在目标区域线程定时重读，玩家线程应用。任务结束条件：
     * 会话关闭、玩家离线/换界面、容器消失或变尺寸。
     */
    private static void scheduleRefresh(JavaPlugin plugin, Player player,
            Location targetLocation, int refreshTicks, Inventory viewInv) {
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        ScheduledTask scheduled = Bukkit.getRegionScheduler().runAtFixedRate(
            plugin, targetLocation,
            task -> {
                ref.set(task);
                ViewSession session = ViewSession.get(player.getUniqueId());
                if (session == null || session.view() != viewInv) {
                    task.cancel();
                    return;
                }
                ContainerSnapshots.ReadResult fresh = ContainerSnapshots.read(targetLocation);
                runOnPlayer(plugin, player, () -> {
                    if (!ViewSession.applyRefresh(player, session, fresh)) {
                        task.cancel();
                    }
                });
            }, refreshTicks, refreshTicks);
        ref.set(scheduled);
        ViewSession.register(player,
            ViewSession.create(targetLocation, viewInv).withCanceller(() -> {
                ScheduledTask current = ref.get();
                if (current != null) {
                    try {
                        current.cancel();
                    } catch (Exception ignored) {
                        // 卸载期取消失败可忽略
                    }
                }
            }));
    }

    private static void runOnPlayer(JavaPlugin plugin, Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.run();
            }
        }, null);
    }
}
