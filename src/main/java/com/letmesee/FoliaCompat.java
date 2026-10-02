package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Folia / Paper 专属逻辑隔离层。
 *
 * <p>本类直接引用 {@code Bukkit.getRegionScheduler()}、{@code Player.getScheduler()}
 * 与 Adventure API，在 Spigot / CraftBukkit 上这些符号不存在。
 * 调用方必须先经 {@link #isSupported()} 确认后再进入，Spigot 路径永远不要触碰本类，
 * 否则会在运行时抛出 {@link NoSuchMethodError} / {@link NoClassDefFoundError}。</p>
 */
public final class FoliaCompat {

    private static final Boolean SUPPORTED = detect();

    private FoliaCompat() {
    }

    public static boolean isSupported() {
        return SUPPORTED;
    }

    private static boolean detect() {
        try {
            Bukkit.class.getMethod("getRegionScheduler");
            Player.class.getMethod("getScheduler");
            Class.forName("net.kyori.adventure.text.Component");
            return true;
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 在目标区域线程读取容器并克隆，再切回玩家线程打开只读视图。
     *
     * @param snapshot 预先在主线程采集的只读快照（世界名、坐标、方块类型名）
     * @param opener   在玩家线程打开视图的回调，参数为克隆好的物品数组与展示用标题
     */
    public static void openContainer(JavaPlugin plugin, Player player, Location targetLocation,
            ContainerSnapshot snapshot, ViewOpener opener) {
        Bukkit.getRegionScheduler().run(plugin, targetLocation, task -> {
            Block block = targetLocation.getBlock();
            BlockState state = block.getState();

            if (!(state instanceof Container container)) {
                runOnPlayer(plugin, player, () ->
                    player.sendMessage("§c该位置没有容器"));
                return;
            }

            Inventory targetInv = container.getInventory();
            ItemStack[] contents = ContainerSnapshots.cloneContents(targetInv.getContents());

            Component customName = container.customName();
            Component body = customName == null || Component.empty().equals(customName)
                ? Component.text(snapshot.displayName())
                : customName;
            Component title = Component.text("[只读] ", NamedTextColor.GRAY).append(body);
            String plainName = PlainTextComponentSerializer.plainText().serialize(body);

            InventoryType type = targetInv.getType();
            int size = targetInv.getSize();

            ContainerSnapshots.audit(plugin, player, targetLocation, block.getType().name(), plainName);

            runOnPlayer(plugin, player, () -> opener.open(contents, type, size, title, plainName));
        });
    }

    private static void runOnPlayer(JavaPlugin plugin, Player player, Runnable action) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                action.run();
            }
        }, null);
    }

    /** 预先在调用线程采集的容器展示信息，避免跨线程触碰 Block。 */
    public record ContainerSnapshot(String displayName) {
    }

    /** 在玩家线程打开只读视图的回调。 */
    @FunctionalInterface
    public interface ViewOpener {
        void open(ItemStack[] contents, InventoryType type, int size, Component title, String plainName);
    }
}
