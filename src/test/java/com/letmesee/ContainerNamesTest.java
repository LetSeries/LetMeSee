package com.letmesee;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerNamesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "CHEST, 箱子",
        "TRAPPED_CHEST, 陷阱箱",
        "BARREL, 木桶",
        "SHULKER_BOX, 潜影盒",
        "WHITE_SHULKER_BOX, 潜影盒",
        "ORANGE_SHULKER_BOX, 潜影盒",
        "MAGENTA_SHULKER_BOX, 潜影盒",
        "LIGHT_BLUE_SHULKER_BOX, 潜影盒",
        "YELLOW_SHULKER_BOX, 潜影盒",
        "LIME_SHULKER_BOX, 潜影盒",
        "PINK_SHULKER_BOX, 潜影盒",
        "GRAY_SHULKER_BOX, 潜影盒",
        "LIGHT_GRAY_SHULKER_BOX, 潜影盒",
        "CYAN_SHULKER_BOX, 潜影盒",
        "PURPLE_SHULKER_BOX, 潜影盒",
        "BLUE_SHULKER_BOX, 潜影盒",
        "BROWN_SHULKER_BOX, 潜影盒",
        "GREEN_SHULKER_BOX, 潜影盒",
        "RED_SHULKER_BOX, 潜影盒",
        "BLACK_SHULKER_BOX, 潜影盒",
        "FURNACE, 熔炉",
        "BLAST_FURNACE, 高炉",
        "SMOKER, 烟熏炉",
        "HOPPER, 漏斗",
        "DROPPER, 投掷器",
        "DISPENSER, 发射器",
        "BREWING_STAND, 酿造台",
        "CRAFTER, 合成器",
        "CHISELED_BOOKSHELF, 雕纹书架",
        "LECTERN, 讲台",
        "JUKEBOX, 唱片机",
        "DECORATED_POT, 饰纹陶罐",
    })
    void knownContainersResolveToChineseName(String materialName, String expected) {
        assertEquals(expected, ContainerNames.displayName(Material.valueOf(materialName)));
    }

    @Test
    void unknownMaterialFallsBackToEnumName() {
        assertEquals("STONE", ContainerNames.displayName(Material.STONE));
        assertEquals("ENDER_CHEST", ContainerNames.displayName(Material.ENDER_CHEST));
    }
}
