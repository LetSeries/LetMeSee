package com.letmesee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinateTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "100, 0, 100",
        "-5, 0, -5",
        "  42 , 0, 42",
        "30000000, 0, 30000000",
        "-30000000, 0, -30000000",
    })
    void absoluteCoordinates(String arg, int base, int expected) {
        assertEquals(expected, LMSCommand.parseCoordinate(arg, base));
    }

    @ParameterizedTest(name = "{0} (base {1}) -> {2}")
    @CsvSource({
        "~, 100, 100",
        "~0, 100, 100",
        "~1, 100, 101",
        "~-2, 100, 98",
        " ~5 , 100, 105",
    })
    void relativeCoordinates(String arg, int base, int expected) {
        assertEquals(expected, LMSCommand.parseCoordinate(arg, base));
    }

    @ParameterizedTest(name = "reject {0}")
    @ValueSource(strings = {
        "abc", "1.5", "", "~a", "~~1",
        "30000001", "-30000001", "9999999999", "-9999999999",
    })
    void invalidAbsoluteCoordinatesRejected(String arg) {
        assertThrows(NumberFormatException.class, () -> LMSCommand.parseCoordinate(arg, 0));
    }

    @Test
    void relativeOverflowRejected() {
        assertThrows(NumberFormatException.class, () -> LMSCommand.parseCoordinate("~1", 30_000_000));
        assertThrows(NumberFormatException.class, () -> LMSCommand.parseCoordinate("~-1", -30_000_000));
    }
}
