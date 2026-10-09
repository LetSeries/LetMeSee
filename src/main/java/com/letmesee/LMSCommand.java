package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LMSCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public LMSCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("letmesee.reload")) {
                sender.sendMessage("§c你没有权限重载配置");
                return true;
            }
            plugin.reloadConfig();
            int maxDistance = LMSConfig.maxTargetDistance(plugin.getConfig());
            boolean audit = LMSConfig.auditEnabled(plugin.getConfig());
            int refreshSeconds = LMSConfig.refreshIntervalSeconds(plugin.getConfig());
            boolean containerView = LMSConfig.containerViewEnabled(plugin.getConfig());
            boolean playerView = LMSConfig.playerViewEnabled(plugin.getConfig());
            sender.sendMessage("§a配置已重载：容器查看 " + onOff(containerView)
                + "，玩家库存查看 " + onOff(playerView)
                + "，最大距离 " + maxDistance + " 格，审计日志 " + onOff(audit)
                + "，刷新间隔 "
                + (refreshSeconds == 0 ? "关闭" : refreshSeconds + " 秒"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (!player.hasPermission("letmesee.use")) {
            player.sendMessage("§c你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
            if (!LMSConfig.containerViewEnabled(plugin.getConfig())) {
                player.sendMessage("§c容器查看功能已在配置中关闭");
                return true;
            }
            int maxDistance = LMSConfig.maxTargetDistance(plugin.getConfig());
            Block targetBlock;
            try {
                targetBlock = player.getTargetBlockExact(maxDistance);
            } catch (IllegalStateException e) {
                player.sendMessage("§c目标不在当前区域，请改用 /lms <世界> <X> <Y> <Z>");
                return true;
            }
            if (targetBlock == null) {
                player.sendMessage("§c请将准星对准一个容器（最大距离 " + maxDistance + " 格）");
                return true;
            }

            openContainer(player, targetBlock.getLocation());
            return true;
        }

        if (args.length == 2
            && (args[0].equalsIgnoreCase("inv") || args[0].equalsIgnoreCase("ec"))) {
            if (!player.hasPermission("letmesee.player")) {
                player.sendMessage("§c你没有权限查看玩家库存");
                return true;
            }
            if (!LMSConfig.playerViewEnabled(plugin.getConfig())) {
                player.sendMessage("§c玩家库存查看功能已在配置中关闭");
                return true;
            }
            boolean enderChest = args[0].equalsIgnoreCase("ec");
            openPlayerView(player, args[1], enderChest);
            return true;
        }

        if (args.length >= 4 && !LMSConfig.containerViewEnabled(plugin.getConfig())) {
            player.sendMessage("§c容器查看功能已在配置中关闭");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage("§c用法: /lms <世界> <X> <Y> <Z>，或 /lms <inv|ec> <玩家>");
            return true;
        }

        if (args.length > 4) {
            player.sendMessage("§c参数过多，用法: /lms <世界> <X> <Y> <Z>");
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            player.sendMessage("§c未找到世界: " + args[0]);
            return true;
        }

        Location origin;
        try {
            origin = player.getLocation();
        } catch (IllegalStateException e) {
            // Folia 下命令执行线程与玩家不在同一区域：相对坐标无基准可用
            origin = null;
        }
        if (origin == null
            && (args[1].trim().startsWith("~")
                || args[2].trim().startsWith("~")
                || args[3].trim().startsWith("~"))) {
            player.sendMessage("§c当前无法读取你的位置，相对坐标（~）不可用，请使用绝对坐标");
            return true;
        }
        int x, y, z;
        try {
            int baseX = origin == null ? 0 : origin.getBlockX();
            int baseY = origin == null ? 0 : origin.getBlockY();
            int baseZ = origin == null ? 0 : origin.getBlockZ();
            x = parseCoordinate(args[1], baseX);
            y = parseCoordinate(args[2], baseY);
            z = parseCoordinate(args[3], baseZ);
        } catch (NumberFormatException e) {
            player.sendMessage("§c坐标必须为 -30000000 到 30000000 之间的整数，相对坐标可用 ~（如 ~ ~1 ~-2）");
            return true;
        }

        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            player.sendMessage("§e该区块尚未加载，请先靠近目标位置再试");
            return true;
        }

        // getMinHeight/getMaxHeight 来自 WorldInfo，Paper 与 Spigot 均有
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        if (y < minY || y >= maxY) {
            player.sendMessage("§cY 坐标超出该世界高度范围（" + minY + " ~ " + (maxY - 1) + "）");
            return true;
        }

        openContainer(player, new Location(world, x, y, z));
        return true;
    }

    private static String onOff(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    /**
     * 解析坐标分量，支持绝对坐标与相对坐标（{@code ~}、{@code ~1}、{@code ~-2}）。
     * 拒绝非数字、越界整数与超出世界边境范围的值，
     * 调用方统一按 NumberFormatException 处理。
     *
     * @param arg 坐标参数
     * @param base 相对坐标的基准（玩家当前位置对应分量）
     */
    static int parseCoordinate(String arg, int base) throws NumberFormatException {
        String text = arg.trim();
        long value;
        if (text.startsWith("~")) {
            String offset = text.substring(1).trim();
            long delta = 0;
            if (!offset.isEmpty()) {
                try {
                    delta = Long.parseLong(offset);
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("bad relative coordinate: " + arg);
                }
            }
            value = (long) base + delta;
        } else {
            try {
                value = Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("not a number: " + arg);
            }
        }
        if (value < -30_000_000L || value > 30_000_000L) {
            throw new NumberFormatException("out of range: " + arg);
        }
        return (int) value;
    }

    /**
     * 只读查看目标玩家的背包或末影箱。目标离线直接提示；
     * Folia 下在目标玩家线程快照，Spigot 下同步直读。
     */
    private void openPlayerView(Player viewer, String targetName, boolean enderChest) {
        // 先精确匹配（大小写敏感），再模糊匹配（忽略大小写、前缀），
        // 输错大小写或只输前缀也能找到
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getPlayer(targetName);
        }
        if (target == null || !target.isOnline()) {
            viewer.sendMessage("§c目标玩家不在线: " + targetName);
            return;
        }
        boolean auditEnabled = LMSConfig.auditEnabled(plugin.getConfig());
        int refreshTicks = LMSConfig.refreshIntervalTicks(plugin.getConfig());
        if (ServerCompat.isFolia()) {
            // 此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
            ServerCompat.openFoliaPlayerView(plugin, viewer, target.getUniqueId(),
                enderChest, auditEnabled, refreshTicks);
            return;
        }
        LegacyRefresher.openPlayerView(plugin, viewer, target, enderChest,
            auditEnabled, refreshTicks);
    }

    private void openContainer(Player player, Location targetLocation) {
        // 注意：调用线程（玩家线程）不触碰目标方块。Folia 下目标可能位于其他区域，
        // 一切读取都在区域线程（FoliaCompat）或同线程（Spigot legacy）内完成。
        // 配置在调用线程预读，区域线程/定时任务不再触碰 config。
        boolean auditEnabled = LMSConfig.auditEnabled(plugin.getConfig());
        int refreshTicks = LMSConfig.refreshIntervalTicks(plugin.getConfig());
        if (ServerCompat.isFolia()) {
            // 此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
            ServerCompat.openFoliaContainer(plugin, player, targetLocation,
                auditEnabled, refreshTicks);
            return;
        }
        LegacyRefresher.open(plugin, player, targetLocation, auditEnabled, refreshTicks);
    }
}
