package com.letmesee;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 与服务端实现无关的共享逻辑：容器读取、物品克隆与审计日志。
 * 只使用 Bukkit + JDK API，在 Paper 与 Spigot 上均可安全运行，
 * 可在任意线程调用（调用方保证线程归属正确）。
 */
public final class ContainerSnapshots {

    private ContainerSnapshots() {
    }

    /** 容器读取结果状态。 */
    public enum ReadStatus {
        OK,
        ENDER_CHEST,
        NOT_CONTAINER,
        EMPTY,
        READ_ERROR
    }

    /** 一次容器读取的结果。非 OK 时除 status 外字段无意义。 */
    public record ReadResult(
        ReadStatus status,
        ItemStack[] contents,
        InventoryType type,
        int size,
        String fullName,
        String blockType,
        BlockState state
    ) {
        static ReadResult fail(ReadStatus status) {
            return new ReadResult(status, null, null, 0, "", "", null);
        }
    }

    /**
     * 读取目标位置的容器并克隆物品。只做纯数据读取，不打开任何界面。
     * 末影箱、私有背包等特殊情况用状态码区分，由调用方决定提示语。
     */
    @SuppressWarnings("deprecation") // getCustomName 在 Spigot API 中是唯一命名接口，未过时
    public static ReadResult read(Location location) {
        final Block block;
        try {
            block = location.getBlock();
        } catch (Exception e) {
            return ReadResult.fail(ReadStatus.READ_ERROR);
        }

        if (block.getType() == Material.ENDER_CHEST) {
            return ReadResult.fail(ReadStatus.ENDER_CHEST);
        }

        final BlockState state;
        final Inventory targetInv;
        final ItemStack[] contents;
        try {
            state = block.getState();
            if (!(state instanceof BlockInventoryHolder holder)) {
                return ReadResult.fail(ReadStatus.NOT_CONTAINER);
            }
            targetInv = holder.getInventory();
            if (targetInv == null) {
                return ReadResult.fail(ReadStatus.EMPTY);
            }
            contents = cloneContents(targetInv.getContents());
        } catch (Exception e) {
            return ReadResult.fail(ReadStatus.READ_ERROR);
        }

        String customName = state instanceof Nameable nameable ? nameable.getCustomName() : null;
        String fullName = (customName == null || customName.isEmpty())
            ? ContainerNames.displayName(block.getType())
            : customName;
        return new ReadResult(ReadStatus.OK, contents, targetInv.getType(), targetInv.getSize(),
            fullName, block.getType().name(), state);
    }

    /** 非 OK 状态对应的玩家提示语。 */
    public static String messageFor(ReadStatus status) {
        return switch (status) {
            case ENDER_CHEST -> "§c末影箱是玩家私有背包，不支持只读查看";
            case NOT_CONTAINER -> "§c该位置没有容器";
            case EMPTY -> "§c无法读取该容器的物品";
            case READ_ERROR -> "§c读取容器失败，请稍后重试";
            case OK -> "";
        };
    }

    public static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            copy[i] = item == null ? null : item.clone();
        }
        return copy;
    }

    /**
     * 截断过长的展示名，避免只读视图标题在客户端显示异常。
     * 审计日志记录的是完整名称，不受影响。
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength || maxLength < 2) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    public static void audit(JavaPlugin plugin, Player player, Location location,
            String blockType, String containerName, boolean enabled) {
        if (!enabled) {
            return;
        }
        plugin.getLogger().info("[审计] " + player.getName() + "(" + player.getUniqueId() + ") 查看了 "
            + describe(location) + " " + blockType + "[" + containerName + "]");
    }

    /** 只读 Location 坐标，不触碰方块，任意线程可调用。 */
    public static String describe(Location location) {
        return location.getWorld().getName()
            + " (" + location.getBlockX() + "," + location.getBlockY() + ","
            + location.getBlockZ() + ")";
    }
}
