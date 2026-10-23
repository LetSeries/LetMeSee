package com.letmesee;

import org.bukkit.configuration.Configuration;

import java.util.Locale;

/**
 * 插件配置视图。只依赖 Bukkit Configuration 数据接口，
 * 插件侧传入 {@code getConfig()}，单测传入 {@code MemoryConfiguration} 即可。
 */
public final class LMSConfig {

    /** 准星模式最大距离默认值。 */
    public static final int DEFAULT_MAX_DISTANCE = 10;

    /** 刷新间隔默认值（秒），0 表示关闭。 */
    public static final int DEFAULT_REFRESH_SECONDS = 5;

    private LMSConfig() {
    }

    /** 是否启用容器查看（/lms 准星模式与坐标模式）。 */
    public static boolean containerViewEnabled(Configuration config) {
        return readToggle(config, "container-view", true, false);
    }

    /** 是否启用玩家库存查看（/lms inv|ec）。 */
    public static boolean playerViewEnabled(Configuration config) {
        return readToggle(config, "player-view", true, false);
    }

    /**
     * 读取布尔开关。
     *
     * <p>{@code Configuration#getBoolean} 只在值确实是 Boolean 时才生效，否则
     * 静默返回默认值——于是 {@code container-view: "false"}（带引号）或写错键名
     * 会让功能「看起来关了其实还开着」。这里显式兼容字符串写法，
     * 其余写法按 {@code failClosed} 处理，开关类配置宁可少开不可多开。</p>
     *
     * @param path 配置键
     * @param defaultValue 键不存在时的默认值
     * @param failClosed 值类型无法识别时的取值
     */
    static boolean readToggle(Configuration config, String path, boolean defaultValue,
            boolean failClosed) {
        Object raw = config.get(path);
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("true")) {
                return true;
            }
            if (normalized.equals("false")) {
                return false;
            }
        }
        return failClosed;
    }

    /** 准星模式最大距离，限制 1~64，非法值回退默认。 */
    public static int maxTargetDistance(Configuration config) {
        int value = config.getInt("max-target-distance", DEFAULT_MAX_DISTANCE);
        if (value < 1 || value > 64) {
            return DEFAULT_MAX_DISTANCE;
        }
        return value;
    }

    /** 是否记录审计日志到控制台。值无法识别时保持开启（审计宁多勿少）。 */
    public static boolean auditEnabled(Configuration config) {
        return readToggle(config, "audit-log", true, true);
    }

    /** 是否将审计日志追加写入插件目录下的 audit.log。 */
    public static boolean auditFileEnabled(Configuration config) {
        return readToggle(config, "audit-file", false, false);
    }

    /**
     * 只读视图刷新间隔（秒）。0 表示关闭自动刷新；
     * 负数或超过 60 视为非法值，回退默认。
     */
    public static int refreshIntervalSeconds(Configuration config) {
        int seconds = config.getInt("refresh-interval-seconds", DEFAULT_REFRESH_SECONDS);
        if (seconds < 0 || seconds > 60) {
            return DEFAULT_REFRESH_SECONDS;
        }
        return seconds;
    }

    /** 只读视图刷新间隔（tick），0 表示关闭。 */
    public static int refreshIntervalTicks(Configuration config) {
        return refreshIntervalSeconds(config) * 20;
    }
}
