package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.EnderChest;
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

    private static final int MAX_TARGET_DISTANCE = 10;

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
            Block targetBlock;
            try {
                targetBlock = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
            } catch (IllegalStateException e) {
                player.sendMessage("§c目标不在当前区域，请改用 /lms <世界> <X> <Y> <Z>");
                return true;
            }
            if (targetBlock == null) {
                player.sendMessage("§c请将准星对准一个容器（最大距离 " + MAX_TARGET_DISTANCE + " 格）");
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

        int x, y, z;
        try {
            x = Integer.parseInt(args[1]);
            y = Integer.parseInt(args[2]);
            z = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c坐标必须为整数");
            return true;
        }

        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            player.sendMessage("§e该区块尚未加载，请先靠近目标位置再试");
            return true;
        }

        openContainer(player, new Location(world, x, y, z));
        return true;
    }

    private void openContainer(Player player, Location targetLocation) {
        Block probe;
        try {
            probe = targetLocation.getBlock();
        } catch (Exception e) {
            player.sendMessage("§c无法读取该位置: " + e.getClass().getSimpleName());
            return;
        }

        if (isEnderChest(probe)) {
            player.sendMessage("§c末影箱是玩家私有背包，不支持只读查看");
            return;
        }

        if (ServerCompat.isFolia()) {
            openFolia(player, targetLocation, probe);
        } else {
            openLegacy(player, targetLocation, probe);
        }
    }

    /** Folia / Paper 路径：经 ServerCompat 反射进入区域线程读取，回到玩家线程打开。 */
    private void openFolia(Player player, Location targetLocation, Block probe) {
        // 注意：此处绝不能直接引用 FoliaCompat，否则 Spigot 上类加载即崩。
        ServerCompat.openFoliaContainer(plugin, player, targetLocation,
            getContainerDisplayName(probe), plainName -> {
            });
    }

    /** Spigot / CraftBukkit 路径：单线程同步直读直开。 */
    @SuppressWarnings("deprecation") // getCustomName 在 Spigot API 中是唯一命名接口，未过时
    private void openLegacy(Player player, Location targetLocation, Block block) {
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
            ? getContainerDisplayName(block)
            : customName;

        ContainerSnapshots.audit(plugin, player, targetLocation, block.getType().name(), containerName);

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

    private static boolean isEnderChest(Block block) {
        if (block.getType() == Material.ENDER_CHEST) {
            return true;
        }
        try {
            return block.getState() instanceof EnderChest;
        } catch (Exception e) {
            return false;
        }
    }

    private String getContainerDisplayName(Block block) {
        return switch (block.getType()) {
            case CHEST -> "箱子";
            case TRAPPED_CHEST -> "陷阱箱";
            case BARREL -> "木桶";
            case SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX ->
                "潜影盒";
            case FURNACE -> "熔炉";
            case BLAST_FURNACE -> "高炉";
            case SMOKER -> "烟熏炉";
            case HOPPER -> "漏斗";
            case DROPPER -> "投掷器";
            case DISPENSER -> "发射器";
            case BREWING_STAND -> "酿造台";
            case CRAFTER -> "合成器";
            case CHISELED_BOOKSHELF -> "雕纹书架";
            case LECTERN -> "讲台";
            case JUKEBOX -> "唱片机";
            case DECORATED_POT -> "饰纹陶罐";
            default -> block.getType().name();
        };
    }
}
