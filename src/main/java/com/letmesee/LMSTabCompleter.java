package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LMSTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            return complete(sender, args);
        } catch (Exception e) {
            // 补全在聊天线程执行，任何异常都不应刷屏：静默降级为空建议
            return List.of();
        }
    }

    private List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("letmesee.use")) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    worlds.add(world.getName());
                }
            }
            return worlds;
        }

        if (!(sender instanceof Player player) || args.length > 4) {
            return List.of();
        }

        String current = args[args.length - 1];
        List<String> result = new ArrayList<>();
        // ~ 不需要读取玩家位置，跨区也能提示
        if ("~".startsWith(current)) {
            result.add("~");
        }
        // 读取玩家位置可能跨区失败，失败时只保留 ~ 建议
        try {
            Location loc = player.getLocation();
            String suggestion = switch (args.length) {
                case 2 -> Integer.toString(loc.getBlockX());
                case 3 -> Integer.toString(loc.getBlockY());
                case 4 -> Integer.toString(loc.getBlockZ());
                default -> null;
            };
            if (suggestion != null && suggestion.startsWith(current)) {
                result.add(suggestion);
            }
        } catch (IllegalStateException e) {
            // 玩家不在当前区域：只返回 ~，不刷屏
        }
        return result;
    }
}
