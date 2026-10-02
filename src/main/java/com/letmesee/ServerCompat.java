package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * 运行环境检测与 Folia 入口反射调用。
 *
 * <p>本类只引用 Bukkit、JDK 与反射 API，在 Spigot / CraftBukkit 上可以安全加载。
 * Folia / Paper 专属逻辑全部隔离在 {@code FoliaCompat} 中，本类通过反射进入，
 * 保证 Spigot 路径永远不会触发其类加载。</p>
 */
public final class ServerCompat {

    private static final boolean FOLIA = detectFolia();

    private ServerCompat() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Bukkit.class.getMethod("getRegionScheduler");
            Player.class.getMethod("getScheduler");
            Class.forName("net.kyori.adventure.text.Component");
            // 确认隔离层本身可加载（方法签名污染会在此处暴露）
            Class.forName("com.letmesee.FoliaCompat");
            return true;
        } catch (NoSuchMethodException | ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * 反射进入 Folia 模式的只读打开流程。
     *
     * @param displayName 预先采集的容器展示名（跨线程不再触碰 Block）
     * @param onOpened   在玩家线程打开视图后的回调，参数为展示用纯文本名
     */
    public static void openFoliaContainer(JavaPlugin plugin, Player player,
            Location targetLocation, String displayName, OpenedCallback onOpened) {
        try {
            Class<?> compat = Class.forName("com.letmesee.FoliaCompat");
            Method method = compat.getMethod("openContainer", JavaPlugin.class, Player.class,
                Location.class, String.class, FoliaOpener.class);
            method.invoke(null, plugin, player, targetLocation, displayName,
                (FoliaOpener) (viewInv, plainName) -> {
                    player.openInventory(viewInv);
                    player.sendMessage("§a已打开 " + plainName + " 的只读视图");
                    onOpened.onOpened(plainName);
                });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Folia 模式进入失败", e);
        }
    }

    /** 在玩家线程打开只读视图后的回调（签名单含 Bukkit/JDK 类型）。 */
    @FunctionalInterface
    public interface FoliaOpener {
        void open(org.bukkit.inventory.Inventory viewInv, String plainName);
    }

    /** 打开成功回调。 */
    @FunctionalInterface
    public interface OpenedCallback {
        void onOpened(String plainName);
    }
}
