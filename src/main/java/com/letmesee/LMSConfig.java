package com.letmesee;

import org.bukkit.configuration.Configuration;

/**
 * 插件配置视图。只依赖 Bukkit Configuration 数据接口，
 * 插件侧传入 {@code getConfig()}，单测传入 {@code MemoryConfiguration} 即可。
 */
public final class LMSConfig {

    /** 准星模式最大距离默认值。 */
    public static final int DEFAULT_MAX_DISTANCE = 10;

    private LMSConfig() {
    }

    /** 准星模式最大距离，限制 1~64，非法值回退默认。 */
    public static int maxTargetDistance(Configuration config) {
        int value = config.getInt("max-target-distance", DEFAULT_MAX_DISTANCE);
        if (value < 1 || value > 64) {
            return DEFAULT_MAX_DISTANCE;
        }
        return value;
    }

    /** 是否记录审计日志。 */
    public static boolean auditEnabled(Configuration config) {
        return config.getBoolean("audit-log", true);
    }

    /**
     * 只读视图刷新间隔（秒）。0 或负数表示关闭自动刷新。
     * 范围限制 1~60，非法值回退默认 5 秒。
     */
    public static int refreshIntervalTicks(Configuration config) {
        int seconds = config.getInt("refresh-interval-seconds", 5);
        if (seconds <= 0) {
            return 0;
        }
        if (seconds > 60) {
            return 60 * 20;
        }
        return seconds * 20;
    }
}
