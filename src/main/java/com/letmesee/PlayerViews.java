package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * 玩家私有库存（背包 / 末影箱）的只读快照与打开逻辑。
 *
 * <p>只引用 Bukkit + JDK API，在 Paper 与 Spigot 上均可安全加载。
 * 纯数据快照部分可在任意线程调用（调用方保证线程归属正确）；
 * 界面打开部分必须在玩家线程执行。</p>
 */
public final class PlayerViews {

    private PlayerViews() {
    }

    /** 私有库存快照。contents 已深拷贝，与原库存脱钩。 */
    public record Snapshot(String ownerName, UUID ownerId, String label,
            ItemStack[] contents, InventoryType type, int size) {
    }

    /**
     * 快照目标玩家的背包或末影箱。必须在目标玩家线程执行（Folia 实体调度器
     * 或 Spigot 主线程）。目标不在线时行为未定义，调用方先检查在线状态。
     */
    public static Snapshot snapshot(Player target, boolean enderChest) {
        Inventory source = enderChest ? target.getEnderChest() : target.getInventory();
        return new Snapshot(target.getName(), target.getUniqueId(),
            enderChest ? "末影箱" : "背包",
            ContainerSnapshots.cloneContents(source.getContents()),
            source.getType(), source.getSize());
    }

    /**
     * 以快照打开只读视图并记审计。必须在查看者（viewer）的玩家线程执行。
     * 刷新会话由调用方按各自调度方式注册，本方法只负责首次打开。
     */
    public static Inventory openSnapshot(JavaPlugin plugin, Player viewer, Snapshot snapshot,
            boolean auditEnabled) {
        String ownerName = snapshot.ownerName();
        String titleName = ContainerSnapshots.truncate(
            ownerName + " 的" + snapshot.label(), 32);
        Inventory viewInv = snapshot.type() == InventoryType.CHEST
            ? Bukkit.createInventory(new ReadOnlyHolder(), snapshot.size(),
                "§7[只读] " + titleName)
            : Bukkit.createInventory(new ReadOnlyHolder(), snapshot.type(),
                "§7[只读] " + titleName);
        viewInv.setContents(snapshot.contents());

        Player owner = Bukkit.getPlayer(snapshot.ownerId());
        String where = owner == null ? "?" : describeTarget(owner);
        plugin.getLogger().info("[审计] " + viewer.getName() + "(" + viewer.getUniqueId()
            + ") 查看了玩家 " + where + "(" + snapshot.ownerId() + ") 的" + snapshot.label());

        viewer.openInventory(viewInv);
        viewer.sendMessage("§a已打开 " + ownerName + " 的" + snapshot.label() + "只读视图");
        return viewInv;
    }

    /** 描述目标玩家位置（审计用），任意线程可调用。 */
    public static String describeTarget(Player target) {
        Location location = null;
        try {
            location = target.getLocation();
        } catch (Exception ignored) {
            // 跨区读不到位置时只记玩家名
        }
        if (location == null || location.getWorld() == null) {
            return target.getName();
        }
        return target.getName() + "@" + ContainerSnapshots.describe(location);
    }
}
