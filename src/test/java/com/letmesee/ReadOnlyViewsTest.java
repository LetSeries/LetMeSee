package com.letmesee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只读标题的格式化规则：前缀固定、正文按可见长度截断，
 * 保证「[只读] 」不会把标题挤过客户端上限。
 */
class ReadOnlyViewsTest {

    @Test
    void titleCarriesPrefix() {
        assertEquals("§7[只读] 箱子", ReadOnlyViews.title("箱子"));
    }

    @Test
    void bodyKeepsLimitBudget() {
        assertEquals(32, ReadOnlyViews.TITLE_LIMIT);
        assertEquals(32 - ReadOnlyViews.PREFIX.length(), ReadOnlyViews.BODY_LIMIT);
    }

    @Test
    void longBodyIsTruncatedToLimit() {
        String visible = ReadOnlyViews.title("x".repeat(80))
            .substring("§7[只读] ".length());

        assertEquals(ReadOnlyViews.BODY_LIMIT, visible.length());
        assertTrue(visible.endsWith("…"));
    }

    @Test
    void bodyAtLimitIsNotTruncated() {
        String exact = "x".repeat(ReadOnlyViews.BODY_LIMIT);

        assertEquals(exact, ReadOnlyViews.body(exact));
    }
}
