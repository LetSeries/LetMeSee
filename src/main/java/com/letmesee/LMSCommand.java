package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;

public class LMSCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public LMSCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (!player.hasPermission("letmesee.use")) {
            player.sendMessage("§c你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
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

        if (args.length < 4) {
            player.sendMessage("§c用法: /lms <世界> <X> <Y> <Z>");
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            player.sendMessage("§c未找到世界: " + args[0]);
            return true;
        }

        Location origin = player.getLocation();
        int x, y, z;
        try {
            x = parseCoordinate(args[1], origin.getBlockX());
            y = parseCoordinate(args[2], origin.getBlockY());
            z = parseCoordinate(args[3], origin.getBlockZ());
        } catch (NumberFormatException e) {
            player.sendMessage("§c坐标必须为 -30000000 到 30000000 之间的整数，相对坐标可用 ~（如 ~ ~1 ~-2）");
            return true;
        }

        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            player.sendMessage("§e该区块尚未加载，请先靠近目标位置再试");
            return true;
        }

        openContainer(player, new Location(world, x, y, z));
        return true;
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

    private void openContainer(Player player, Location targetLocation) {
        // 注意：调用线程（玩家线程）不触碰目标方块。Folia 下目标可能位于其他区域，
        // 一切读取都在区域线程（FoliaCompat）或同线程（Spigot legacy）内完成。
        if (ServerCompat.isFolia()) {
            // 此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
            // 配置在调用线程预读，区域线程内不再触碰 config。
            ServerCompat.openFoliaContainer(plugin, player, targetLocation,
                LMSConfig.auditEnabled(plugin.getConfig()));
            return;
        }

        Block block;
        try {
            block = targetLocation.getBlock();
        } catch (Exception e) {
            plugin.getLogger().warning("[LetMeSee] 无法读取 "
                + ContainerSnapshots.describe(targetLocation) + ": " + e);
            player.sendMessage("§c无法读取该位置，请稍后重试");
            return;
        }
        openLegacy(player, targetLocation, block);
    }

    /** Spigot / CraftBukkit 路径：单线程同步直读直开。 */
    @SuppressWarnings("deprecation") // getCustomName 在 Spigot API 中是唯一命名接口，未过时
    private void openLegacy(Player player, Location targetLocation, Block block) {
        if (block.getType() == Material.ENDER_CHEST) {
            player.sendMessage("§c末影箱是玩家私有背包，不支持只读查看");
            return;
        }

        BlockState state = block.getState();

        if (!(state instanceof BlockInventoryHolder holder)) {
            player.sendMessage("§c该位置没有容器");
            return;
        }

        Inventory targetInv = holder.getInventory();
        if (targetInv == null) {
            player.sendMessage("§c无法读取该容器的物品");
            return;
        }
        ItemStack[] contents = ContainerSnapshots.cloneContents(targetInv.getContents());

        String customName = state instanceof Nameable nameable ? nameable.getCustomName() : null;
        String containerName = (customName == null || customName.isEmpty())
            ? ContainerNames.displayName(block.getType())
            : customName;

        ContainerSnapshots.audit(plugin, player, targetLocation, block.getType().name(),
            containerName, LMSConfig.auditEnabled(plugin.getConfig()));

        InventoryType type = targetInv.getType();
        Inventory viewInv = type == InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), targetInv.getSize(),
                "§7[只读] " + containerName)
            : Bukkit.createInventory(new ReadOnlyHolder(), type,
                "§7[只读] " + containerName);
        viewInv.setContents(contents);
        player.openInventory(viewInv);
        player.sendMessage("§a已打开 " + containerName + " 的只读视图");
    }
}
