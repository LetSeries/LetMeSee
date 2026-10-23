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

import java.util.List;
import java.util.UUID;
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
     * Folia 卸载入口：立即清理会话与定时任务，再尽力把「关闭只读界面」
     * 投递到各查看者的实体线程。
     *
     * <p>插件禁用后提交的实体任务可能被调度器直接拒绝（走 retired 回调），
     * 因此这里只做 best-effort：绝不从卸载线程直接触碰 {@code Player} API，
     * 投递失败时界面会随玩家连接断开关闭。</p>
     */
    public static void closeAll(JavaPlugin plugin) {
        List<UUID> viewers = ViewSession.viewerIds();
        ViewSession.closeAll();
        for (UUID viewerId : viewers) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null) {
                continue;
            }
            try {
                player.getScheduler().run(plugin, task -> {
                    if (player.isOnline()
                        && player.getOpenInventory().getTopInventory().getHolder()
                            instanceof ReadOnlyHolder) {
                        player.closeInventory();
                    }
                }, null);
            } catch (Exception ignored) {
                // 插件已禁用或实体已退休：调度器拒绝投递，界面随连接断开关闭
            }
        }
    }

    /**
     * 在目标区域线程读取容器并克隆，组装只读视图后切回玩家线程打开，
     * 并按配置注册自动刷新。
     *
     * <p>公开方法签名只含 Bukkit / JDK 类型，避免调用方加载期被 Adventure
     * 方法签名污染；Adventure 只出现在方法体内部。界面打开由本类在玩家线程
     * 直接完成（{@link #runOnPlayer} 已保证线程归属），不再经由回调转手。</p>
     */
    public static void openContainer(JavaPlugin plugin, Player player, Location targetLocation,
            boolean auditEnabled, boolean auditFileEnabled, int refreshTicks) {
        Bukkit.getRegionScheduler().run(plugin, targetLocation, task -> {
            try {
                readAndOpen(plugin, player, targetLocation, auditEnabled, auditFileEnabled,
                    refreshTicks);
            } catch (Exception e) {
                plugin.getLogger().warning("[LetMeSee] 读取容器失败 "
                    + ContainerSnapshots.describe(targetLocation) + ": " + e);
                runOnPlayer(plugin, player, () ->
                    player.sendMessage("§c读取容器失败，请稍后重试"));
            }
        });
    }

    private static void readAndOpen(JavaPlugin plugin, Player player, Location targetLocation,
            boolean auditEnabled, boolean auditFileEnabled, int refreshTicks) {
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
        String shownName = ReadOnlyViews.body(rawPlain);
        Component body = shownName.equals(rawPlain) ? rawBody : Component.text(shownName);
        Component title = Component.text(ReadOnlyViews.PREFIX, NamedTextColor.GRAY)
            .append(body);

        ReadOnlyHolder holder = new ReadOnlyHolder();
        InventoryType type = result.type();
        Inventory viewInv = type == InventoryType.CHEST
            ? Bukkit.createInventory(holder, result.size(), title)
            : Bukkit.createInventory(holder, type, title);
        holder.attach(viewInv);
        viewInv.setContents(result.contents());

        String plainName = shownName;
        String where = ContainerSnapshots.describe(targetLocation);
        String blockType = result.blockType();
        String fullName = result.fullName();
        runOnPlayer(plugin, player, () -> {
            player.openInventory(viewInv);
            if (player.getOpenInventory().getTopInventory() != viewInv) {
                player.sendMessage("§c只读视图未能打开（可能被其它插件拦截）");
                return;
            }
            player.sendMessage("§a已打开 " + plainName + " 的只读视图 §7" + where);
            // 审计在界面确实打开后写入，避免把失败或被打断的打开记成一次成功查看
            ContainerSnapshots.audit(plugin, player, targetLocation, blockType,
                fullName, auditEnabled, auditFileEnabled);
            ViewSession session = ViewSession.create(targetLocation, viewInv);
            if (refreshTicks > 0) {
                session = session.withCanceller(
                    scheduleRefresh(plugin, player, targetLocation, refreshTicks, viewInv));
            }
            ViewSession.register(player, session);
        });
    }

    /**
     * 在目标区域线程定时重读，玩家线程应用。返回取消器。
     * 任务结束条件：会话关闭、玩家离线/换界面、容器消失或变尺寸。
     */
    private static Runnable scheduleRefresh(JavaPlugin plugin, Player player,
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
                    if (SessionGuard.shouldStop(player, false, plugin.getConfig())) {
                        player.closeInventory();
                        player.sendMessage(SessionGuard.revokeMessage(false));
                        ViewSession.close(player.getUniqueId());
                        task.cancel();
                        return;
                    }
                    if (!ViewSession.applyRefresh(player, session, fresh)) {
                        task.cancel();
                    }
                });
            }, refreshTicks, refreshTicks);
        ref.set(scheduled);
        return () -> cancelQuietly(ref);
    }

    /** 静默取消定时任务，卸载期失败可忽略。 */
    private static void cancelQuietly(AtomicReference<ScheduledTask> ref) {
        ScheduledTask current = ref.get();
        if (current != null) {
            try {
                current.cancel();
            } catch (Exception ignored) {
                // 卸载期取消失败可忽略
            }
        }
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
     * <p>界面由 {@link PlayerViews#openSnapshot} 在查看者线程打开（此处已由
     * {@link #runOnPlayer} 保证线程归属），因此不再额外传入「打开回调」——
     * 曾经多调一次 {@code openInventory} 会为一个界面触发多余的关闭事件。</p>
     */
    public static void openPlayerView(JavaPlugin plugin, Player viewer,
            java.util.UUID targetId, boolean enderChest, boolean auditEnabled,
            boolean auditFileEnabled, int refreshTicks) {
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
                    auditEnabled, auditFileEnabled);
                if (viewInv == null) {
                    return;
                }
                ViewSession session = ViewSession.createForPlayer(viewInv, targetId);
                if (refreshTicks > 0) {
                    session = session.withCanceller(schedulePlayerRefresh(plugin, viewer,
                        targetId, enderChest, refreshTicks, viewInv));
                }
                ViewSession.register(viewer, session);
            });
        }, () -> runOnPlayer(plugin, viewer,
            () -> viewer.sendMessage("§c目标玩家已离线或不在可访问区域，无法读取库存")));
    }

    /**
     * 在被查看目标线程定时重快照，查看者线程应用。返回取消器。
     * 任务结束条件：会话关闭、任一玩家离线/换界面、目标库存类型变化。
     */
    private static Runnable schedulePlayerRefresh(JavaPlugin plugin, Player viewer,
            java.util.UUID targetId, boolean enderChest, int refreshTicks,
            Inventory viewInv) {
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            return () -> {
            };
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
                    if (SessionGuard.shouldStop(viewer, true, plugin.getConfig())) {
                        viewer.closeInventory();
                        viewer.sendMessage(SessionGuard.revokeMessage(true));
                        ViewSession.close(viewer.getUniqueId());
                        task.cancel();
                        return;
                    }
                    if (!ViewSession.applyPlayerRefresh(viewer, current, fresh)) {
                        task.cancel();
                    }
                });
            }, () -> {
                runOnPlayer(plugin, viewer, () -> {
                    viewer.closeInventory();
                    ViewSession.close(viewer.getUniqueId());
                });
            }, refreshTicks, refreshTicks);
        ref.set(scheduled);
        return () -> cancelQuietly(ref);
    }
}
