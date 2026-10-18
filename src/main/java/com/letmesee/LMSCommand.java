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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LMSCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public LMSCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限与开关的判断全部收敛到 CommandRouter（纯函数、有单测），
        // 这里只负责把决策翻译成具体动作和提示语
        CommandRouter.Access access = new CommandRouter.Access(
            sender instanceof Player,
            sender.hasPermission("letmesee.use"),
            sender.hasPermission("letmesee.player"),
            sender.hasPermission("letmesee.reload"),
            LMSConfig.containerViewEnabled(plugin.getConfig()),
            LMSConfig.playerViewEnabled(plugin.getConfig()));
        CommandRouter.Decision decision = CommandRouter.route(access, args);

        if (!decision.allowed()) {
            sender.sendMessage(denialMessage(decision.denial()));
            return true;
        }

        if (decision.route() == CommandRouter.Route.RELOAD) {
            reload(sender);
            return true;
        }

        // 路由已保证剩余路径都是玩家发起的
        Player player = (Player) sender;
        switch (decision.route()) {
            case CONTAINER_TARGET -> openTargetedContainer(player);
            case CONTAINER_COORDS -> openContainerAt(player, args);
            case PLAYER_VIEW -> openPlayerView(player, args[1],
                args[0].equalsIgnoreCase("ec"));
            case RELOAD, REJECTED -> {
                // 不可能到达：上面已处理
            }
        }
        return true;
    }

    /** 重载配置并回显生效值。控制台与玩家共用。 */
    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        int maxDistance = LMSConfig.maxTargetDistance(plugin.getConfig());
        boolean audit = LMSConfig.auditEnabled(plugin.getConfig());
        boolean auditFile = LMSConfig.auditFileEnabled(plugin.getConfig());
        int refreshSeconds = LMSConfig.refreshIntervalSeconds(plugin.getConfig());
        boolean containerView = LMSConfig.containerViewEnabled(plugin.getConfig());
        boolean playerView = LMSConfig.playerViewEnabled(plugin.getConfig());
        sender.sendMessage("§a配置已重载：容器查看 " + onOff(containerView)
            + "，玩家库存查看 " + onOff(playerView)
            + "，最大距离 " + maxDistance + " 格，审计日志控制台 " + onOff(audit)
            + " / 文件 " + onOff(auditFile)
            + "，刷新间隔 "
            + (refreshSeconds == 0 ? "关闭" : refreshSeconds + " 秒"));
    }

    /** 准星模式：读取玩家正对的方块并打开只读视图。 */
    private void openTargetedContainer(Player player) {
        int maxDistance = LMSConfig.maxTargetDistance(plugin.getConfig());
        Block targetBlock;
        try {
            targetBlock = player.getTargetBlockExact(maxDistance);
        } catch (IllegalStateException e) {
            player.sendMessage("§c目标不在当前区域，请改用 /lms <世界> <X> <Y> <Z>");
            return;
        }
        if (targetBlock == null) {
            player.sendMessage("§c请将准星对准一个容器（最大距离 " + maxDistance + " 格）");
            return;
        }
        openContainer(player, targetBlock.getLocation());
    }

    /** 坐标模式：校验世界、区块、高度与坐标格式后打开。 */
    private void openContainerAt(Player player, String[] args) {
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            player.sendMessage("§c未找到世界: " + args[0]);
            return;
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
            return;
        }

        int x;
        int y;
        int z;
        try {
            int baseX = origin == null ? 0 : origin.getBlockX();
            int baseY = origin == null ? 0 : origin.getBlockY();
            int baseZ = origin == null ? 0 : origin.getBlockZ();
            x = parseCoordinate(args[1], baseX);
            y = parseCoordinate(args[2], baseY);
            z = parseCoordinate(args[3], baseZ);
        } catch (NumberFormatException e) {
            player.sendMessage("§c坐标必须为 -30000000 到 30000000 之间的整数，"
                + "相对坐标可用 ~（如 ~ ~1 ~-2）");
            return;
        }

        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            player.sendMessage("§e该区块尚未加载，请先靠近目标位置再试");
            return;
        }

        // getMinHeight/getMaxHeight 来自 WorldInfo，Paper 与 Spigot 均有
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        if (y < minY || y >= maxY) {
            player.sendMessage("§cY 坐标超出该世界高度范围（" + minY + " ~ " + (maxY - 1) + "）");
            return;
        }

        openContainer(player, new Location(world, x, y, z));
    }

    private static String onOff(boolean enabled) {
        return enabled ? "开启" : "关闭";
    }

    private static String denialMessage(CommandRouter.Denial denial) {
        return switch (denial) {
            case NOT_PLAYER -> "§c只有玩家可以使用此命令";
            case NO_USE_PERMISSION -> "§c你没有权限使用此命令";
            case NO_PLAYER_PERMISSION -> "§c你没有权限查看玩家库存";
            case NO_RELOAD_PERMISSION -> "§c你没有权限重载配置";
            case CONTAINER_DISABLED -> "§c容器查看功能已在配置中关闭";
            case PLAYER_DISABLED -> "§c玩家库存查看功能已在配置中关闭";
            case USAGE -> "§c用法: /lms <世界> <X> <Y> <Z>，或 /lms <inv|ec> <玩家>";
            case NONE -> "";
        };
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
     * Folia 下在目标玩家线程快照，其余服务端同步直读。
     */
    private void openPlayerView(Player viewer, String targetName, boolean enderChest) {
        // 1) 精确匹配（大小写敏感）；2) 唯一前缀匹配（忽略大小写）；
        // 多个候选时拒绝打开并列出，防止看错人
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            String lowerPrefix = targetName.toLowerCase(Locale.ROOT);
            List<Player> candidates = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    candidates.add(online);
                }
            }
            if (candidates.isEmpty()) {
                viewer.sendMessage("§c目标玩家不在线: " + targetName);
                return;
            }
            if (candidates.size() > 1) {
                StringBuilder names = new StringBuilder();
                for (Player candidate : candidates) {
                    if (names.length() > 0) {
                        names.append("、");
                    }
                    names.append(candidate.getName());
                }
                viewer.sendMessage("§c匹配到多个玩家，请补全全名: " + names);
                return;
            }
            target = candidates.get(0);
        }
        if (!target.isOnline()) {
            viewer.sendMessage("§c目标玩家不在线: " + targetName);
            return;
        }
        boolean auditEnabled = LMSConfig.auditEnabled(plugin.getConfig());
        boolean auditFileEnabled = LMSConfig.auditFileEnabled(plugin.getConfig());
        int refreshTicks = LMSConfig.refreshIntervalTicks(plugin.getConfig());
        if (ServerCompat.isFolia()) {
            // 此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
            ServerCompat.openFoliaPlayerView(plugin, viewer, target.getUniqueId(),
                enderChest, auditEnabled, auditFileEnabled, refreshTicks);
            return;
        }
        LegacyRefresher.openPlayerView(plugin, viewer, target, enderChest,
            auditEnabled, auditFileEnabled, refreshTicks);
    }

    private void openContainer(Player player, Location targetLocation) {
        // 注意：调用线程（玩家线程）不触碰目标方块。Folia 下目标可能位于其他区域，
        // 一切读取都在区域线程（FoliaCompat）或同线程（Spigot legacy）内完成。
        // 配置在调用线程预读，区域线程/定时任务不再触碰 config。
        boolean auditEnabled = LMSConfig.auditEnabled(plugin.getConfig());
        boolean auditFileEnabled = LMSConfig.auditFileEnabled(plugin.getConfig());
        int refreshTicks = LMSConfig.refreshIntervalTicks(plugin.getConfig());
        if (ServerCompat.isFolia()) {
            // 此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
            ServerCompat.openFoliaContainer(plugin, player, targetLocation,
                auditEnabled, auditFileEnabled, refreshTicks);
            return;
        }
        LegacyRefresher.open(plugin, player, targetLocation,
            auditEnabled, auditFileEnabled, refreshTicks);
    }
}
