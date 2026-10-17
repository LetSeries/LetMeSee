package com.letmesee;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class LetMeSee extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginCommand command = getCommand("lms");
        if (command == null) {
            getLogger().severe("plugin.yml 中未声明 lms 命令，插件无法工作，请检查安装是否完整");
            return;
        }
        command.setExecutor(new LMSCommand(this));
        command.setTabCompleter(new LMSTabCompleter(this));
        getServer().getPluginManager().registerEvents(new InventoryListener(), this);

        if (ServerCompat.isFolia()) {
            getLogger().info("LetMeSee 已启用（Folia 区域调度模式：按目标区域/实体线程读写）");
        } else {
            getLogger().info("LetMeSee 已启用（Bukkit 同步模式：Paper/Spigot 单线程直读）");
        }
        getLogger().info("配置：容器查看 " + onOff(LMSConfig.containerViewEnabled(getConfig()))
            + "，玩家库存 " + onOff(LMSConfig.playerViewEnabled(getConfig()))
            + "，最大距离 " + LMSConfig.maxTargetDistance(getConfig()) + " 格"
            + "，刷新 " + refreshText() + "，审计日志 "
            + onOff(LMSConfig.auditEnabled(getConfig())));
    }

    private String refreshText() {
        int seconds = LMSConfig.refreshIntervalSeconds(getConfig());
        return seconds == 0 ? "关闭" : seconds + " 秒";
    }

    private static String onOff(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    @Override
    public void onDisable() {
        ViewSession.closeAll();
        getLogger().info("LetMeSee 已禁用");
    }
}
