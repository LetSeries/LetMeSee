package com.letmesee;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * 只读虚拟库存的统一创建入口与标题格式化。
 *
 * <p>容器查看（Folia / Spigot 两条路径）与玩家库存查看共用同一套标题规则，
 * 避免三处各写一遍 {@code createInventory} 导致标题长度、前缀、类型分支不一致。</p>
 *
 * <p>只引用 Bukkit + JDK，在任何服务端实现与线程上都可安全加载。</p>
 */
public final class ReadOnlyViews {

    /** 客户端容器标题的可见字符上限。 */
    public static final int TITLE_LIMIT = 32;

    /** 标题前缀（可见 5 字符，颜色码不计）。 */
    public static final String PREFIX = "[只读] ";

    /** § 格式标题前缀。 */
    private static final String LEGACY_PREFIX = "§7" + PREFIX;

    /** 标题正文可用的可见字符数。 */
    public static final int BODY_LIMIT = TITLE_LIMIT - PREFIX.length();

    private ReadOnlyViews() {
    }

    /**
     * 生成 § 格式的只读标题。正文超长时按可见长度截断，
     * 保证前缀不会把标题挤到客户端显示上限之外。
     */
    public static String title(String body) {
        return LEGACY_PREFIX + ContainerSnapshots.truncate(body, BODY_LIMIT);
    }

    /** 按可见长度截断标题正文（供 Adventure 标题路径使用）。 */
    public static String body(String raw) {
        return ContainerSnapshots.truncate(raw, BODY_LIMIT);
    }

    /**
     * 创建带 {@link ReadOnlyHolder} 的虚拟只读库存。
     *
     * <p>{@code CHEST} 是唯一允许自定义格数的类型（单箱 27 / 双箱 54），
     * 其余类型按 {@code InventoryType} 的固定格式创建。</p>
     *
     * <p>标题用 § 字符串而非 Adventure：本类在 Spigot 上也会加载，
     * 不能出现 Adventure 类型签名。Folia 路径需要保留组件样式，
     * 因此自行用 {@link #body(String)} 组装 Component。</p>
     *
     * @param type 库存类型
     * @param size 仅 {@code CHEST} 使用的格数
     * @param body 标题正文（不含前缀）
     */
    @SuppressWarnings("deprecation") // String 标题是 Spigot 与 Bukkit 唯一通用的接口
    public static Inventory create(InventoryType type, int size, String body) {
        ReadOnlyHolder holder = new ReadOnlyHolder();
        String title = title(body);
        Inventory view = type == InventoryType.CHEST
            ? Bukkit.createInventory(holder, size, title)
            : Bukkit.createInventory(holder, type, title);
        holder.attach(view);
        return view;
    }
}
