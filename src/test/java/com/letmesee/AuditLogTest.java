package com.letmesee;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    @Test
    void formatUsesStableUtcTimestamp() {
        String line = AuditLog.format("[审计] 测试记录", Instant.parse("2025-03-04T05:06:07.008Z"));

        assertEquals("2025-03-04T05:06:07.008Z [审计] 测试记录", line);
    }

    @Test
    void formatEscapesLineBreaksToPreventLogForging() {
        String line = AuditLog.format("viewer\n[伪造] 未授权查看\u2028another", Instant.EPOCH);

        assertTrue(line.startsWith("1970-01-01T00:00:00.000Z "));
        assertEquals(1, line.lines().count());
        assertTrue(line.contains("viewer [伪造] 未授权查看 another"));
    }
}
