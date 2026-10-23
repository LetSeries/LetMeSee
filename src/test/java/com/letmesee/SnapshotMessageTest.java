package com.letmesee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 读取失败状态的玩家提示：每条都要可读，OK 状态没有提示。
 */
class SnapshotMessageTest {

    @Test
    void okStatusHasNoMessage() {
        assertEquals("", ContainerSnapshots.messageFor(ContainerSnapshots.ReadStatus.OK));
    }

    @Test
    void everyFailureStatusHasANonEmptyMessage() {
        for (ContainerSnapshots.ReadStatus status : ContainerSnapshots.ReadStatus.values()) {
            if (status == ContainerSnapshots.ReadStatus.OK) {
                continue;
            }
            String message = ContainerSnapshots.messageFor(status);

            assertFalse(message.isEmpty(), status.name());
            assertTrue(message.startsWith("§"), status.name());
        }
    }

    @Test
    void enderChestMessageExplainsWhyItIsPrivate() {
        String message = ContainerSnapshots.messageFor(ContainerSnapshots.ReadStatus.ENDER_CHEST);

        assertTrue(message.contains("末影箱"));
        assertTrue(message.contains("不支持"));
    }

    @Test
    void missingContainerSaysSo() {
        assertTrue(ContainerSnapshots.messageFor(ContainerSnapshots.ReadStatus.NOT_CONTAINER)
            .contains("没有容器"));
    }
}
