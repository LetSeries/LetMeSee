package com.letmesee;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已打开会话的持续授权：撤销权限或关闭功能后，刷新必须停下会话。
 * 这里只测纯判定部分（不涉及 Bukkit 事件与调度）。
 */
class SessionGuardTest {

    private static MemoryConfiguration config() {
        return new MemoryConfiguration();
    }

    @Test
    void containerSessionStopsWhenUsePermissionRevoked() {
        assertTrue(SessionGuard.shouldStop(false, false, true, config()));
    }

    @Test
    void containerSessionStopsWhenFeatureDisabled() {
        MemoryConfiguration config = config();
        config.set("container-view", false);

        assertTrue(SessionGuard.shouldStop(false, true, true, config));
    }

    @Test
    void containerSessionContinuesWhenStillAuthorised() {
        assertFalse(SessionGuard.shouldStop(false, true, false, config()));
    }

    @Test
    void playerSessionStopsWhenPlayerPermissionRevoked() {
        // 只撤 letmesee.player 也必须停：玩家库存会话不依赖 letmesee.use
        assertTrue(SessionGuard.shouldStop(true, true, false, config()));
    }

    @Test
    void playerSessionStopsWhenFeatureDisabled() {
        MemoryConfiguration config = config();
        config.set("player-view", false);

        assertTrue(SessionGuard.shouldStop(true, false, true, config));
    }

    @Test
    void playerSessionContinuesWithoutUsePermission() {
        assertFalse(SessionGuard.shouldStop(true, false, true, config()));
    }

    @Test
    void containerSessionIgnoresPlayerPermission() {
        assertFalse(SessionGuard.shouldStop(false, true, false, config()));
    }

    @Test
    void revokeMessagesDifferBySessionType() {
        assertTrue(SessionGuard.revokeMessage(true).contains("玩家库存"));
        assertTrue(SessionGuard.revokeMessage(false).contains("容器"));
    }
}
