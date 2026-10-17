package com.letmesee;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷新时「内容未变就跳过重写」的判定逻辑。
 * 判定过宽会导致视图不更新，过窄会导致每次刷新都整批重发库存包。
 *
 * <p>{@code ItemStack} 的公开构造器需要服务端注册表，零服务端单测里用
 * {@link TestStack}（只覆盖 {@code equals}）代表一个真实物品，从而覆盖
 * 「逐格比较委托给 {@code ItemStack.equals}」这一分支。</p>
 */
class SameContentsTest {

    /** 测试替身：仅保留相等性语义。 */
    private static final class TestStack extends ItemStack {
        private final String id;

        TestStack(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestStack stack && id.equals(stack.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    @Test
    void sameReferenceIsAlwaysSame() {
        ItemStack[] contents = {null, null, null};

        assertTrue(ContainerSnapshots.sameContents(contents, contents));
    }

    @Test
    void bothNullIsSame() {
        assertTrue(ContainerSnapshots.sameContents(null, null));
    }

    @Test
    void nullAndArrayDiffer() {
        assertFalse(ContainerSnapshots.sameContents(null, new ItemStack[0]));
        assertFalse(ContainerSnapshots.sameContents(new ItemStack[0], null));
    }

    @Test
    void lengthMismatchDiffers() {
        assertFalse(ContainerSnapshots.sameContents(new ItemStack[3], new ItemStack[4]));
    }

    @Test
    void equalEmptySlotsAreSame() {
        assertTrue(ContainerSnapshots.sameContents(new ItemStack[9], new ItemStack[9]));
    }

    @Test
    void equalItemsAreSame() {
        ItemStack[] current = {new TestStack("stone"), null};
        ItemStack[] fresh = {new TestStack("stone"), null};

        assertTrue(ContainerSnapshots.sameContents(current, fresh));
    }

    @Test
    void differentItemsAreDetected() {
        ItemStack[] current = {new TestStack("stone")};
        ItemStack[] fresh = {new TestStack("dirt")};

        assertFalse(ContainerSnapshots.sameContents(current, fresh));
    }

    @Test
    void slotBecomingFilledIsDetected() {
        assertFalse(ContainerSnapshots.sameContents(
            new ItemStack[]{null}, new ItemStack[]{new TestStack("stone")}));
    }

    @Test
    void slotBecomingEmptyIsDetected() {
        assertFalse(ContainerSnapshots.sameContents(
            new ItemStack[]{new TestStack("stone")}, new ItemStack[]{null}));
    }
}
