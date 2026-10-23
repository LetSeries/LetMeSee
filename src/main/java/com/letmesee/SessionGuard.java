package com.letmesee;

import org.bukkit.configuration.Configuration;

/**
 * 已打开会话的持续授权检查。
 *
 * <p>权限与功能开关只在「打开视图」时检查是不够的：管理员被撤销
 * {@code letmesee.use} / {@code letmesee.player}，或服主把 {@code container-view} /
 * {@code player-view} 关掉并执行 {@code /lms reload} 之后，已经打开的只读视图仍会
 * 按刷新间隔持续同步目标内容——相当于关闭开关后还在继续偷看。
 * 因此每次刷新前都要重新校验一次。</p>
 *
 * <p>判定部分是纯函数（只依赖 Configuration 与布尔量），可离线单测。</p>
 */
public final class SessionGuard {

    private SessionGuard() {
    }

    /**
     * 会话是否应当因授权变化而立即结束。
     *
     * @param playerSession 是否为玩家库存会话（{@code /lms inv|ec}）
     * @param hasUsePermission 查看者当前是否仍有 {@code letmesee.use}
     * @param hasPlayerPermission 查看者当前是否仍有 {@code letmesee.player}
     * @param config 当前配置
     * @return true 表示应关闭界面并结束会话
     */
    public static boolean shouldStop(boolean playerSession, boolean hasUsePermission,
            boolean hasPlayerPermission, Configuration config) {
        if (playerSession) {
            return !hasPlayerPermission || !LMSConfig.playerViewEnabled(config);
        }
        return !hasUsePermission || !LMSConfig.containerViewEnabled(config);
    }

    /** 便捷重载：直接取查看者当前权限。必须在玩家线程调用。 */
    public static boolean shouldStop(org.bukkit.entity.Player viewer, boolean playerSession,
            Configuration config) {
        return shouldStop(playerSession, viewer.hasPermission("letmesee.use"),
            viewer.hasPermission("letmesee.player"), config);
    }

    /** 会话结束原因对应的玩家提示。 */
    public static String revokeMessage(boolean playerSession) {
        return playerSession
            ? "§e玩家库存查看权限或功能已关闭，视图已结束"
            : "§e容器查看权限或功能已关闭，视图已结束";
    }
}
