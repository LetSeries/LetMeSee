package com.letmesee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TruncateTest {

    @ParameterizedTest(name = "[{0}] max {1} -> [{2}]")
    @CsvSource({
        "箱子, 32, 箱子",
        "'12345678901234567890123456789012', 32, '12345678901234567890123456789012'",
        "'123456789012345678901234567890123', 32, '1234567890123456789012345678901…'",
    })
    void truncate(String text, int max, String expected) {
        assertEquals(expected, ContainerSnapshots.truncate(text, max));
    }

    @Test
    void nullPassesThrough() {
        assertNull(ContainerSnapshots.truncate(null, 32));
    }
}
