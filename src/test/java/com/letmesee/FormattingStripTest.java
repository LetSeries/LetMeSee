package com.letmesee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 展示层清洗：容器名由玩家可控（改名潜影盒即可），标题与聊天消息里的
 * {@code §} 代码必须去掉，防止重绘「[只读]」标签或伪造容器身份。
 * 审计记录保留完整原名，不属于本测试范围。
 */
class FormattingStripTest {

    @Test
    void legacyCodesAreRemoved() {
        assertEquals("箱子名字", ContainerSnapshots.stripFormatting("§a箱子§r名字"));
    }

    @Test
    void nullAndCleanTextPassThrough() {
        assertNull(ContainerSnapshots.stripFormatting(null));
        assertEquals("箱子", ContainerSnapshots.stripFormatting("箱子"));
    }

    @Test
    void loneSectionSignIsKept() {
        assertEquals("箱子§", ContainerSnapshots.stripFormatting("箱子§"));
    }

    @Test
    void titleStaysReadableAndWithinBudget() {
        String title = ReadOnlyViews.title("§c[只读] §a钻石§l块");

        assertTrue(title.startsWith("§7[只读] "));
        assertFalse(title.substring("§7[只读] ".length()).contains("§"));
    }

    @Test
    void bodyStopsFormattingFromHidingTheLimit() {
        String body = ReadOnlyViews.body("§a" + "名".repeat(40));

        // 截断按清洗后的可见字符计，颜色代码不占预算
        assertEquals(ReadOnlyViews.BODY_LIMIT, body.length());
        assertFalse(body.contains("§"));
        assertTrue(body.endsWith("…"));
    }
}
