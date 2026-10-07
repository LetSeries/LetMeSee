package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Nameable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

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

        // 有 Adventure 自定义名时保留样式（颜色/斜体），超长才回退纯文本截断
        Component customName = result.state() instanceof Nameable nameable
            ? nameable.customName() : null;
        Component rawBody = customName == null || Component.empty().equals(customName)
            ? Component.text(result.fullName())
            : customName;
        String rawPlain = PlainTextComponentSerializer.plainText().serialize(rawBody);
        String shownName = ContainerSnapshots.truncate(rawPlain, 32);
        Component body = shownName.equals(rawPlain) ? rawBody : Component.text(shownName);
        Component title = Component.text("[只读] ", NamedTextColor.GRAY).append(body);

        InventoryType type = result.type();
        Inventory viewInv = type == InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), result.size(), title)
            : Bukkit.createInventory(new ReadOnlyHolder(), type, title);
        viewInv.setContents(result.contents());

        ContainerSnapshots.audit(plugin, player, targetLocation, result.blockType(),
            result.fullName(), auditEnabled);

        String plainName = shownName;
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
            ViewSession.create(targetLocation, viewInv, null).withCanceller(() -> {
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

    /**
     * 在目标玩家线程快照其背包/末影箱，切回查看者线程打开只读视图，
     * 并按配置注册自动刷新。
     *
     * <p>公开方法签名只含 Bukkit / JDK 类型。被查看目标离线时直接提示查看者。</p>
     *
     * @param opener 在查看者线程打开视图的回调，收到已建好的只读视图与快照
     */
    public static void openPlayerView(JavaPlugin plugin, Player viewer,
            java.util.UUID targetId, boolean enderChest, boolean auditEnabled,
            int refreshTicks, ServerCompat.FoliaPlayerOpener opener) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            viewer.sendMessage("§c目标玩家不在线");
            return;
        }
        target.getScheduler().run(plugin, task -> {
            final PlayerViews.Snapshot snapshot;
            try {
                snapshot = PlayerViews.snapshot(target, enderChest);
            } catch (Exception e) {
                plugin.getLogger().warning("[LetMeSee] 快照玩家库存失败 "
                    + targetId + ": " + e);
                runOnPlayer(plugin, viewer, () ->
                    viewer.sendMessage("§c读取玩家库存失败，请稍后重试"));
                return;
            }
            runOnPlayer(plugin, viewer, () -> {
                Inventory viewInv = PlayerViews.openSnapshot(plugin, viewer, snapshot,
                    auditEnabled);
                opener.open(viewInv, snapshot);
                if (refreshTicks > 0) {
                    schedulePlayerRefresh(plugin, viewer, targetId, enderChest,
                        refreshTicks, viewInv);
                }
            });
        }, () -> viewer.sendMessage("§c目标玩家不在可访问区域"));
    }

    /**
     * 在被查看目标线程定时重快照，查看者线程应用。任务结束条件：
     * 会话关闭、任一玩家离线/换界面、目标库存类型变化。
     */
    private static void schedulePlayerRefresh(JavaPlugin plugin, Player viewer,
            java.util.UUID targetId, boolean enderChest, int refreshTicks,
            Inventory viewInv) {
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return;
        }
        ScheduledTask scheduled = target.getScheduler().runAtFixedRate(plugin,
            task -> {
                ref.set(task);
                ViewSession session = ViewSession.get(viewer.getUniqueId());
                if (session == null || session.view() != viewInv) {
                    task.cancel();
                    return;
                }
                if (!ViewSession.isOwnerOnline(session)) {
                    runOnPlayer(plugin, viewer, () -> {
                        viewer.closeInventory();
                        viewer.sendMessage("§e目标玩家已下线，视图已关闭");
                        ViewSession.close(viewer.getUniqueId());
                    });
                    task.cancel();
                    return;
                }
                final PlayerViews.Snapshot fresh;
                try {
                    Player online = Bukkit.getPlayer(targetId);
                    if (online == null) {
                        throw new IllegalStateException("target offline");
                    }
                    fresh = PlayerViews.snapshot(online, enderChest);
                } catch (Exception e) {
                    runOnPlayer(plugin, viewer, () -> {
                        viewer.closeInventory();
                        viewer.sendMessage("§e无法读取目标库存，视图已关闭");
                        ViewSession.close(viewer.getUniqueId());
                    });
                    task.cancel();
                    return;
                }
                runOnPlayer(plugin, viewer, () -> {
                    ViewSession current = ViewSession.get(viewer.getUniqueId());
                    if (current == null || current.view() != viewInv) {
                        task.cancel();
                        return;
                    }
                    if (fresh.type() != viewInv.getType() || fresh.size() != viewInv.getSize()) {
                        viewer.closeInventory();
                        viewer.sendMessage("§e目标库存已变化，请重新打开");
                        ViewSession.close(viewer.getUniqueId());
                        task.cancel();
                        return;
                    }
                    Inventory open;
                    try {
                        open = viewer.getOpenInventory().getTopInventory();
                    } catch (Exception e) {
                        ViewSession.close(viewer.getUniqueId());
                        task.cancel();
                        return;
                    }
                    if (open != viewInv) {
                        ViewSession.close(viewer.getUniqueId());
                        task.cancel();
                        return;
                    }
                    viewInv.setContents(fresh.contents());
                    try {
                        viewer.updateInventory();
                    } catch (Exception ignored) {
                        // 客户端同步失败不影响服务端数据
                    }
                });
            }, () -> {
                runOnPlayer(plugin, viewer, () -> {
                    viewer.closeInventory();
                    ViewSession.close(viewer.getUniqueId());
                });
            }, refreshTicks, refreshTicks);
        ref.set(scheduled);
        // 注意：会话注册必须先于 openInventory 触发的 CloseEvent？不，
        // 注册在这里（仍在查看者线程回调内），顺序与容器路径一致。
        ViewSession.register(viewer,
            ViewSession.create(null, viewInv, targetId).withCanceller(() -> {
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
}
