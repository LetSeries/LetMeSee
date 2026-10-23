package com.letmesee;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * bStats 匿名统计接入。
 *
 * <p>统计内容只有服务器元数据（服务端类型与版本、在线玩家数、插件数量等），
 * <b>不包含</b>任何容器内容、玩家行为或 {@code /lms} 的使用记录。</p>
 *
 * <p>两道保险：插件 ID 未配置时直接跳过；bStats 类缺失（例如 build.ps1
 * 手工构建的产物不含统计依赖）时降级为一条日志，绝不影响插件功能。</p>
 */
public final class MetricsSupport {

    /**
     * bStats 插件 ID。
     *
     * <p>在 <a href="https://bstats.org/what-is-my-plugin-id">bstats.org</a>
     * 注册 LetMeSee 后，把这里的 0 换成页面给出的数字即可启用统计；
     * 保持 0 或负数表示暂不启用。</p>
     */
    static final int PLUGIN_ID = 0;

    private MetricsSupport() {
    }

    /**
     * 经由方法读取插件 ID，而不是在判断里直接写 {@code PLUGIN_ID}：
     * 常量表达式会被 javac 折叠，条件为假时整个统计分支会被当作死代码裁掉，
     * 编译产物里将看不到任何统计逻辑。
     */
    static int pluginId() {
        return PLUGIN_ID;
    }

    /** 在 onEnable 调用；未配置 ID 或依赖缺失时跳过统计。 */
    public static void start(JavaPlugin plugin) {
        if (pluginId() <= 0) {
            plugin.getLogger().info("bStats 统计未启用：尚未配置插件 ID"
                + "（注册后在 MetricsSupport.PLUGIN_ID 填入）");
            return;
        }
        try {
            Metrics metrics = new Metrics(plugin, PLUGIN_ID);
            // 自定义图表需要在 bStats 后台登记同名图表，未登记会被服务端忽略
            metrics.addCustomChart(new SimplePie("server_type",
                () -> ServerCompat.isFolia() ? "Folia" : "Paper/Spigot"));
        } catch (Throwable t) {
            // 统计永远不能影响插件启动（含 NoClassDefFoundError：产物未打包 bStats）
            plugin.getLogger().info("bStats 统计初始化跳过: " + t);
        }
    }
}
