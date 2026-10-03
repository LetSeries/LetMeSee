package com.letmesee;

import org.bukkit.plugin.java.JavaPlugin;

public class LetMeSee extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("lms").setExecutor(new LMSCommand(this));
        getCommand("lms").setTabCompleter(new LMSTabCompleter());
        getServer().getPluginManager().registerEvents(new InventoryListener(), this);

        if (ServerCompat.isFolia()) {
            getLogger().info("LetMeSee 已启用 (Folia/Paper 区域调度模式)");
        } else {
            getLogger().warning("未检测到 Folia 调度 API，已降级为 Bukkit 同步模式"
                + " (Spigot/CraftBukkit)");
        }
    }

    @Override
    public void onDisable() {
        ViewSession.closeAll();
        getLogger().info("LetMeSee 已禁用");
    }
}
