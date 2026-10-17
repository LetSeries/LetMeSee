package com.letmesee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限路由的纯函数测试。这里是历史缺陷的回归防线：
 * {@code /lms inv|ec} 曾因主命令先检查 {@code letmesee.use} 而无法只授
 * {@code letmesee.player} 使用。
 */
class CommandRouterTest {

    private static CommandRouter.Access player(boolean use, boolean playerView) {
        return new CommandRouter.Access(true, use, playerView, false, true, true);
    }

    private static CommandRouter.Access console(boolean reload) {
        return new CommandRouter.Access(false, true, true, reload, true, true);
    }

    private static CommandRouter.Decision route(CommandRouter.Access access, String... args) {
        return CommandRouter.route(access, args);
    }

    @Test
    void targetedViewNeedsUsePermission() {
        CommandRouter.Decision decision = route(player(false, false));

        assertEquals(CommandRouter.Route.REJECTED, decision.route());
        assertEquals(CommandRouter.Denial.NO_USE_PERMISSION, decision.denial());
    }

    @Test
    void targetedViewOpensWhenAllowed() {
        CommandRouter.Decision decision = route(player(true, false));

        assertTrue(decision.allowed());
        assertEquals(CommandRouter.Route.CONTAINER_TARGET, decision.route());
    }

    @Test
    void targetedViewRespectsFeatureToggle() {
        CommandRouter.Access access = new CommandRouter.Access(
            true, true, true, true, false, true);

        CommandRouter.Decision decision = route(access);

        assertEquals(CommandRouter.Denial.CONTAINER_DISABLED, decision.denial());
    }

    @Test
    void playerViewWorksWithoutUsePermission() {
        // 回归：只授 letmesee.player 也必须能看玩家库存
        CommandRouter.Decision decision = route(player(false, true), "inv", "Steve");

        assertTrue(decision.allowed());
        assertEquals(CommandRouter.Route.PLAYER_VIEW, decision.route());
    }

    @Test
    void playerViewNeedsPlayerPermission() {
        CommandRouter.Decision decision = route(player(true, false), "ec", "Steve");

        assertEquals(CommandRouter.Denial.NO_PLAYER_PERMISSION, decision.denial());
    }

    @Test
    void playerViewRespectsFeatureToggle() {
        CommandRouter.Access access = new CommandRouter.Access(
            true, true, true, true, true, false);

        assertEquals(CommandRouter.Denial.PLAYER_DISABLED,
            route(access, "inv", "Steve").denial());
    }

    @Test
    void playerViewKeywordsAreCaseInsensitive() {
        assertEquals(CommandRouter.Route.PLAYER_VIEW,
            route(player(false, true), "INV", "Steve").route());
        assertEquals(CommandRouter.Route.PLAYER_VIEW,
            route(player(false, true), "Ec", "Steve").route());
    }

    @Test
    void coordinateViewNeedsUsePermissionAndToggle() {
        CommandRouter.Access noPermission = new CommandRouter.Access(
            true, false, true, true, true, true);
        CommandRouter.Access disabled = new CommandRouter.Access(
            true, true, true, true, false, true);

        assertEquals(CommandRouter.Denial.NO_USE_PERMISSION,
            route(noPermission, "world", "1", "2", "3").denial());
        assertEquals(CommandRouter.Denial.CONTAINER_DISABLED,
            route(disabled, "world", "1", "2", "3").denial());
        assertEquals(CommandRouter.Route.CONTAINER_COORDS,
            route(player(true, false), "world", "1", "2", "3").route());
    }

    @Test
    void reloadIsAvailableToConsole() {
        CommandRouter.Decision decision = route(console(true), "reload");

        assertTrue(decision.allowed());
        assertEquals(CommandRouter.Route.RELOAD, decision.route());
    }

    @Test
    void reloadIsRejectedWithoutPermission() {
        CommandRouter.Decision decision = route(console(false), "reload");

        assertEquals(CommandRouter.Denial.NO_RELOAD_PERMISSION, decision.denial());
    }

    @Test
    void consoleCannotOpenViews() {
        assertEquals(CommandRouter.Denial.NOT_PLAYER, route(console(true)).denial());
        assertEquals(CommandRouter.Denial.NOT_PLAYER,
            route(console(true), "inv", "Steve").denial());
    }

    @Test
    void wrongArgumentShapesFallBackToUsage() {
        for (String[] args : new String[][]{
            {"inv"},
            {"foo"},
            {"world", "1"},
            {"world", "1", "2"},
            {"world", "1", "2", "3", "extra"},
            {"reload", "extra"},
        }) {
            CommandRouter.Decision decision = route(player(true, true), args);
            assertEquals(CommandRouter.Denial.USAGE, decision.denial(),
                String.join(" ", args));
            assertFalse(decision.allowed());
        }
    }

    @Test
    void decisionAllowedMatchesDenial() {
        assertTrue(new CommandRouter.Decision(CommandRouter.Route.RELOAD,
            CommandRouter.Denial.NONE).allowed());
        assertFalse(new CommandRouter.Decision(CommandRouter.Route.REJECTED,
            CommandRouter.Denial.USAGE).allowed());
    }
}
