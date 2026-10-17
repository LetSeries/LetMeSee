package com.letmesee;

import java.util.Locale;

/**
 * {@code /lms} 的路由决策：把「调用者是谁、有哪些权限、功能是否开启、参数长什么样」
 * 收敛成纯函数，便于单测，也避免主命令里权限判断顺序出错。
 *
 * <p>权限规则：{@code letmesee.use} 只管容器查看，{@code letmesee.player} 只管
 * {@code inv|ec}，{@code letmesee.reload} 只管重载。三者互不牵连——
 * 只授予 {@code letmesee.player} 的玩家也必须能查看玩家库存。</p>
 */
public final class CommandRouter {

    /** 命令最终走哪条执行路径。 */
    public enum Route {
        /** 重载配置（控制台可用）。 */
        RELOAD,
        /** 准星模式：查看正对的容器。 */
        CONTAINER_TARGET,
        /** 坐标模式：查看指定坐标的容器。 */
        CONTAINER_COORDS,
        /** 玩家库存模式：{@code inv|ec}。 */
        PLAYER_VIEW,
        /** 拒绝执行，原因见 {@link Decision#denial()}。 */
        REJECTED
    }

    /** 拒绝原因；每种对应一条玩家提示。 */
    public enum Denial {
        NONE,
        NOT_PLAYER,
        NO_USE_PERMISSION,
        NO_PLAYER_PERMISSION,
        NO_RELOAD_PERMISSION,
        CONTAINER_DISABLED,
        PLAYER_DISABLED,
        USAGE
    }

    /** 调用者上下文：身份、权限与功能开关。 */
    public record Access(boolean player, boolean use, boolean playerView, boolean reload,
            boolean containerEnabled, boolean playerEnabled) {
    }

    /** 一次路由结果。denial 为 {@link Denial#NONE} 时按 route 执行。 */
    public record Decision(Route route, Denial denial) {
        public boolean allowed() {
            return denial == Denial.NONE;
        }
    }

    private CommandRouter() {
    }

    /**
     * 根据调用者上下文与参数决定执行路径。纯函数，不触碰服务端。
     *
     * @param access 调用者身份、权限与功能开关
     * @param args 命令参数（不含命令名本身）
     */
    public static Decision route(Access access, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return access.reload()
                ? new Decision(Route.RELOAD, Denial.NONE)
                : reject(Denial.NO_RELOAD_PERMISSION);
        }

        // 其余子命令都依赖玩家实体（准星、相对坐标、库存界面）
        if (!access.player()) {
            return reject(Denial.NOT_PLAYER);
        }

        if (args.length == 0) {
            if (!access.use()) {
                return reject(Denial.NO_USE_PERMISSION);
            }
            return access.containerEnabled()
                ? new Decision(Route.CONTAINER_TARGET, Denial.NONE)
                : reject(Denial.CONTAINER_DISABLED);
        }

        if (args.length == 2 && isPlayerViewKeyword(args[0])) {
            if (!access.playerView()) {
                return reject(Denial.NO_PLAYER_PERMISSION);
            }
            return access.playerEnabled()
                ? new Decision(Route.PLAYER_VIEW, Denial.NONE)
                : reject(Denial.PLAYER_DISABLED);
        }

        if (args.length == 4) {
            if (!access.use()) {
                return reject(Denial.NO_USE_PERMISSION);
            }
            return access.containerEnabled()
                ? new Decision(Route.CONTAINER_COORDS, Denial.NONE)
                : reject(Denial.CONTAINER_DISABLED);
        }

        return reject(Denial.USAGE);
    }

    static boolean isPlayerViewKeyword(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        return lower.equals("inv") || lower.equals("ec");
    }

    private static Decision reject(Denial denial) {
        return new Decision(Route.REJECTED, denial);
    }
}
