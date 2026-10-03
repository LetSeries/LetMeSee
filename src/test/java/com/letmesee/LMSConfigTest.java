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
        assertEquals(5 * 20, LMSConfig.refreshIntervalTicks(new MemoryConfiguration()));
    }

    @Test
    void refreshIntervalZeroDisables() {
        for (int off : new int[]{0, -1, -60}) {
            MemoryConfiguration config = new MemoryConfiguration();
            config.set("refresh-interval-seconds", off);

            assertEquals(0, LMSConfig.refreshIntervalTicks(config), "seconds=" + off);
        }
    }

    @Test
    void refreshIntervalConvertsAndCaps() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("refresh-interval-seconds", 10);
        assertEquals(10 * 20, LMSConfig.refreshIntervalTicks(config));

        MemoryConfiguration capped = new MemoryConfiguration();
        capped.set("refresh-interval-seconds", 3600);
        assertEquals(60 * 20, LMSConfig.refreshIntervalTicks(capped));
    }
}
