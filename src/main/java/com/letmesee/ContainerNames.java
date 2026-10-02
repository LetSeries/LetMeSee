package com.letmesee;

import org.bukkit.Material;

/**
 * 方块类型到中文展示名的映射。
 *
 * <p>只依赖 Bukkit + JDK，可在任意线程、任意服务端实现上调用。
 * Folia 路径在区域线程内调用，调用线程不再为取展示名而触碰方块。</p>
 */
public final class ContainerNames {

    private ContainerNames() {
    }

    public static String displayName(Material type) {
        return switch (type) {
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
            default -> type.name();
        };
    }
}
