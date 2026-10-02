package com.letmesee;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 只测不依赖服务端运行时的数组结构语义。
 * 元素深拷贝（ItemStack.clone）是 Bukkit API 契约，无需 Mock 服务端，
 * 此处只验证：长度保持、null 保持、返回新数组。
 */
class ContainerSnapshotsTest {

    @Test
    void emptyArrayClonesToEmptyArray() {
        ItemStack[] copy = ContainerSnapshots.cloneContents(new ItemStack[0]);

        assertArrayEquals(new ItemStack[0], copy);
    }

    @Test
    void nullsArePreservedAndArrayIsNew() {
        ItemStack[] original = {null, null, null};

        ItemStack[] copy = ContainerSnapshots.cloneContents(original);

        assertNotSame(original, copy);
        assertArrayEquals(original, copy);
        for (ItemStack item : copy) {
            assertNull(item);
        }
    }
}
