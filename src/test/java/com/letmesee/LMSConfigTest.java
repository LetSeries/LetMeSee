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
}
