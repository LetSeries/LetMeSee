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
        boolean canUse = sender.hasPermission("letmesee.use");
        boolean canReload = sender.hasPermission("letmesee.reload");
        if (!canUse && !canReload) {
            return List.of();
        }

        if (args.length == 1) {
            boolean canPlayer =
                sender.hasPermission("letmesee.player") && sender instanceof Player;
            if (!canUse) {
                List<String> only = new ArrayList<>();
                if (canReload && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    only.add("reload");
                }
                if (canPlayer) {
                    for (String keyword : new String[]{"inv", "ec"}) {
                        if (keyword.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                            only.add(keyword);
                        }
                    }
                }
                return only;
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            if (sender.hasPermission("letmesee.reload") && "reload".startsWith(prefix)) {
                result.add("reload");
            }
            if (canPlayer) {
                for (String keyword : new String[]{"inv", "ec"}) {
                    if (keyword.startsWith(prefix)) {
                        result.add(keyword);
                    }
                }
            }
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(world.getName());
                }
            }
            return result;
        }

        // /lms inv|ec <玩家>：补在线玩家名
        if (args.length == 2 && sender.hasPermission("letmesee.player")
            && (args[0].equalsIgnoreCase("inv") || args[0].equalsIgnoreCase("ec"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
            return result;
        }

        if (!canUse || !(sender instanceof Player player) || args.length > 4) {
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
