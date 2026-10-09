package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 只读查看会话管理：一个玩家同时最多持有一个会话。
 * 只使用 Bukkit + JDK API，与服务端实现无关；线程安全。
 *
 * <p>会话在以下情况结束：玩家关闭界面、退出游戏、容器消失或变化、
 * 插件卸载。结束时定时任务一并取消，不泄漏。</p>
 */
public record ViewSession(Location location, Inventory view, Runnable canceller,
        UUID ownerId) {

    private static final Map<UUID, ViewSession> SESSIONS = new ConcurrentHashMap<>();

    /** 创建尚未注册的会话（canceller 为空实现，注册时替换）。ownerId 为被查看目标，可为 null。 */
    public static ViewSession create(Location location, Inventory view, UUID ownerId) {
        return new ViewSession(location, view, () -> {
        }, ownerId);
    }

    /** 容器会话快捷创建（无被查看目标）。 */
    public static ViewSession create(Location location, Inventory view) {
        return create(location, view, null);
    }

    /** 玩家库存会话快捷创建（无方块位置）。 */
    public static ViewSession createForPlayer(Inventory view, UUID ownerId) {
        return create(null, view, ownerId);
    }

    public ViewSession withCanceller(Runnable canceller) {
        return new ViewSession(location, view, canceller, ownerId);
    }

    /** 被查看目标是否仍在线（容器会话 ownerId 为 null 时视为存活）。 */
    public static boolean isOwnerOnline(ViewSession session) {
        return session.ownerId() == null || Bukkit.getPlayer(session.ownerId()) != null;
    }

    /** 注册会话，顶掉该玩家旧会话（旧定时任务一并取消）。 */
    public static ViewSession register(Player player, ViewSession session) {
        close(player.getUniqueId());
        SESSIONS.put(player.getUniqueId(), session);
        return session;
    }

    public static ViewSession get(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void close(Player player) {
        close(player.getUniqueId());
    }

    public static void close(UUID playerId) {
        ViewSession session = SESSIONS.remove(playerId);
        if (session != null) {
            try {
                session.canceller().run();
            } catch (Exception ignored) {
                // 取消定时任务失败不影响会话清理
            }
        }
    }

    /** 插件卸载时关闭全部会话：取消定时任务并关闭仍打开的界面。 */
    public static void closeAll() {
        for (UUID playerId : new ArrayList<>(SESSIONS.keySet())) {
            close(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                try {
                    player.closeInventory();
                } catch (Exception ignored) {
                    // 卸载期关闭界面失败可忽略
                }
            }
        }
    }

    /**
     * 把一次刷新内容应用到视图的通用逻辑：在线检查、界面归属检查、
     * 内容写入、客户端同步。调用方先做各自的类型/状态校验。
     * 必须在玩家线程执行。
     *
     * @param closedMessage 会话结束且需要关闭界面时的提示（null 则静默清理）
     * @return 会话是否继续存活；false 表示调用方应停止定时任务
     */
    public static boolean applyContents(Player player, ViewSession session,
            org.bukkit.inventory.ItemStack[] contents, String closedMessage) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline()) {
            close(playerId);
            return false;
        }

        Inventory open;
        try {
            open = player.getOpenInventory().getTopInventory();
        } catch (Exception e) {
            close(playerId);
            return false;
        }
        if (open != session.view()) {
            // 玩家已不在本会话界面（手动关闭或打开了别的界面）
            close(playerId);
            return false;
        }

        if (contents == null) {
            player.closeInventory();
            if (closedMessage != null) {
                player.sendMessage(closedMessage);
            }
            close(playerId);
            return false;
        }

        // 写入前复查：会话可能在检查间隙被顶掉（新开视图），
        // 旧定时任务不得写入新会话的界面
        if (get(playerId) != session) {
            return false;
        }
        session.view().setContents(contents);
        try {
            player.updateInventory();
        } catch (Exception ignored) {
            // 客户端同步失败不影响服务端数据
        }
        return true;
    }

    /**
     * 把一次刷新读取应用到视图。必须在玩家线程执行。
     *
     * @return 会话是否继续存活；false 表示调用方应停止定时任务
     */
    public static boolean applyRefresh(Player player, ViewSession session,
            ContainerSnapshots.ReadResult result) {
        if (result.status() != ContainerSnapshots.ReadStatus.OK) {
            // 读取失败：关闭界面并提示，内容传 null 触发清理分支
            return applyContents(player, session, null, "§e容器已变化或不存在，视图已关闭");
        }
        if (result.type() != session.view().getType()
            || result.size() != session.view().getSize()) {
            player.closeInventory();
            player.sendMessage("§e容器大小已变化，请重新打开");
            close(player.getUniqueId());
            return false;
        }
        return applyContents(player, session, result.contents(), null);
    }

    /**
     * 把一次玩家库存重快照应用到视图。必须在查看者线程执行。
     *
     * @return 会话是否继续存活；false 表示调用方应停止定时任务
     */
    public static boolean applyPlayerRefresh(Player viewer, ViewSession session,
            PlayerViews.Snapshot fresh) {
        if (fresh.type() != session.view().getType()
            || fresh.size() != session.view().getSize()) {
            viewer.closeInventory();
            viewer.sendMessage("§e目标库存已变化，请重新打开");
            close(viewer.getUniqueId());
            return false;
        }
        return applyContents(viewer, session, fresh.contents(), null);
    }
}
