package com.letmesee;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LMSConfigTest {

    @Test
    void defaultsWhenEmpty() {
        MemoryConfiguration config = new MemoryConfiguration();

        assertEquals(LMSConfig.DEFAULT_MAX_DISTANCE, LMSConfig.maxTargetDistance(config));
        assertTrue(LMSConfig.auditEnabled(config));
        assertTrue(LMSConfig.containerViewEnabled(config));
        assertTrue(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void featureTogglesCanBeDisabled() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("container-view", false);
        config.set("player-view", false);

        assertFalse(LMSConfig.containerViewEnabled(config));
        assertFalse(LMSConfig.playerViewEnabled(config));
    }

    @Test
    void validDistanceIsUsed() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("max-target-distance", 32);

        assertEquals(32, LMSConfig.maxTargetDistance(config));
    }

    @Test
    void outOfRangeDistanceFallsBackToDefault() {
        for (int bad : new int[]{0, -5, 65, 1000}) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("max-target-distance", bad);

            assertEquals(LMSConfig.DEFAULT_MAX_DISTANCE, LMSConfig.maxTargetDistance(config),
                "distance=" + bad);
        }
    }

    @Test
    void auditCanBeDisabled() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("audit-log", false);

        assertFalse(LMSConfig.auditEnabled(config));
    }

    @Test
    void refreshIntervalDefaultsToFiveSeconds() {
        assertEquals(5, LMSConfig.refreshIntervalSeconds(new MemoryConfiguration()));
        assertEquals(5 * 20, LMSConfig.refreshIntervalTicks(new MemoryConfiguration()));
    }

    @Test
    void refreshIntervalZeroDisables() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("refresh-interval-seconds", 0);

        assertEquals(0, LMSConfig.refreshIntervalSeconds(config));
        assertEquals(0, LMSConfig.refreshIntervalTicks(config));
    }

    @Test
    void refreshIntervalOutOfRangeFallsBackToDefault() {
        for (int bad : new int[]{-1, -60, 61, 3600}) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("refresh-interval-seconds", bad);

            assertEquals(LMSConfig.DEFAULT_REFRESH_SECONDS,
                LMSConfig.refreshIntervalSeconds(config), "seconds=" + bad);
        }
    }

    @Test
    void refreshIntervalConvertsToTicks() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("refresh-interval-seconds", 10);

        assertEquals(10, LMSConfig.refreshIntervalSeconds(config));
        assertEquals(10 * 20, LMSConfig.refreshIntervalTicks(config));
    }
}
