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
        } catch (ReflectiveOperationException | LinkageError e) {
            // 方法缺失、类缺失、NoClassDefFoundError /
            // ExceptionInInitializerError 等：隔离层不可用，降级为同步模式
            return false;
        }
    }

    /**
     * 反射进入 Folia 模式的只读打开流程。
     *
     * <p>调用线程不触碰目标方块，一切读取都在区域线程内完成。
     * 反射失败时给玩家友好提示，不抛异常。</p>
     */
    public static void openFoliaContainer(JavaPlugin plugin, Player player,
            Location targetLocation, boolean auditEnabled, int refreshTicks) {
        try {
            Method method = FoliaMethods.openContainer();
            method.invoke(null, plugin, player, targetLocation, auditEnabled, refreshTicks,
                (FoliaOpener) (viewInv, plainName, where) -> {
                    player.openInventory(viewInv);
                    player.sendMessage("§a已打开 " + plainName + " 的只读视图 §7" + where);
                });
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[LetMeSee] Folia 模式进入失败，已取消本次查看: " + e);
            player.sendMessage("§c打开只读视图失败，请稍后重试");
        }
    }

    /**
     * Folia 反射句柄懒缓存。独立嵌套类，JVM 加载是 lazy 的：
     * Spigot 上 {@code isFolia()} 为 false，调用方永远不会执行到这里，
     * 因此永远不会触发 {@code FoliaCompat} 类加载。
     */
    private static final class FoliaMethods {
        private static volatile Method openContainer;
        private static volatile Method openPlayerView;

        static Method openContainer() throws ReflectiveOperationException {
            Method cached = openContainer;
            if (cached == null) {
                synchronized (FoliaMethods.class) {
                    cached = openContainer;
                    if (cached == null) {
                        cached = Class.forName("com.letmesee.FoliaCompat").getMethod(
                            "openContainer", JavaPlugin.class, Player.class,
                            Location.class, boolean.class, int.class, FoliaOpener.class);
                        openContainer = cached;
                    }
                }
            }
            return cached;
        }

        static Method openPlayerView() throws ReflectiveOperationException {
            Method cached = openPlayerView;
            if (cached == null) {
                synchronized (FoliaMethods.class) {
                    cached = openPlayerView;
                    if (cached == null) {
                        cached = Class.forName("com.letmesee.FoliaCompat").getMethod(
                            "openPlayerView", JavaPlugin.class, Player.class,
                            java.util.UUID.class, boolean.class, boolean.class,
                            int.class, FoliaPlayerOpener.class);
                        openPlayerView = cached;
                    }
                }
            }
            return cached;
        }
    }

    /** 在玩家线程打开只读视图的回调（签名单含 Bukkit/JDK 类型）。 */
    @FunctionalInterface
    public interface FoliaOpener {
        void open(org.bukkit.inventory.Inventory viewInv, String plainName, String where);
    }

    /**
     * 反射进入 Folia 模式的玩家库存只读流程（背包 / 末影箱）。
     * 在目标玩家线程快照，切回查看者线程打开。反射失败时友好提示。
     */
    public static void openFoliaPlayerView(JavaPlugin plugin, Player viewer,
            java.util.UUID targetId, boolean enderChest, boolean auditEnabled,
            int refreshTicks) {
        try {
            Method method = FoliaMethods.openPlayerView();
            method.invoke(null, plugin, viewer, targetId, enderChest, auditEnabled,
                refreshTicks, (FoliaPlayerOpener) (viewInv, snapshot) -> {
                    viewer.openInventory(viewInv);
                });
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[LetMeSee] Folia 玩家视图进入失败: " + e);
            viewer.sendMessage("§c打开只读视图失败，请稍后重试");
        }
    }

    /** 玩家库存视图打开回调（签名单含 Bukkit/JDK 类型）。 */
    @FunctionalInterface
    public interface FoliaPlayerOpener {
        void open(org.bukkit.inventory.Inventory viewInv,
            com.letmesee.PlayerViews.Snapshot snapshot);
    }
}
