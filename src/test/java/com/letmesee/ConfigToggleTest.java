package com.letmesee;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布尔开关的解析：{@code Configuration#getBoolean} 只在值确实是 Boolean 时生效，
 * 带引号的 "false" 会静默回退默认值（true），让「以为关了其实还开着」。
 * 这里固定住修正后的语义。
 */
class ConfigToggleTest {

    @Test
    void realBooleansAreHonoured() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("container-view", false);
        config.set("player-view", false);

        assertFalse(LMSConfig.containerViewEnabled(config));
        assertFalse(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void quotedBooleansAreHonoured() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("container-view", "false");
        config.set("player-view", "true");

        assertFalse(LMSConfig.containerViewEnabled(config));
        assertTrue(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void quotedBooleansIgnoreCaseAndSpaces() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("container-view", "  FALSE ");
        config.set("player-view", "True");

        assertFalse(LMSConfig.containerViewEnabled(config));
        assertTrue(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void unrecognisedValuesFailClosedForViewToggles() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("container-view", "no");
        config.set("player-view", 1);

        assertFalse(LMSConfig.containerViewEnabled(config));
        assertFalse(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void auditFlagsKeepSafeDefaultsForUnrecognisedValues() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("audit-log", "yes");
        config.set("audit-file", "maybe");

        // 审计宁多勿少：控制台保持开启，文件保持关闭
        assertTrue(LMSConfig.auditEnabled(config));
        assertFalse(LMSConfig.auditFileEnabled(config));
    }

    @Test
    void missingKeysUseDefaults() {
        MemoryConfiguration config = new MemoryConfiguration();

        assertTrue(LMSConfig.containerViewEnabled(config));
        assertTrue(LMSConfig.playerViewEnabled(config));
        assertTrue(LMSConfig.auditEnabled(config));
        assertFalse(LMSConfig.auditFileEnabled(config));
    }
}
