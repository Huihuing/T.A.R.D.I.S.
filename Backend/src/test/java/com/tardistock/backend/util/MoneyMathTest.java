package com.tardistock.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyMathTest {

    @Test
    void roundsPositiveHalfCentAwayFromZero() {
        assertEquals(1.01, MoneyMath.roundCents(1.005), 0.0000001);
    }

    @Test
    void roundsNegativeHalfCentAwayFromZero() {
        assertEquals(-1.01, MoneyMath.roundCents(-1.005), 0.0000001);
    }

    @Test
    void preservesNormalCentValues() {
        assertEquals(123.45, MoneyMath.roundCents(123.45), 0.0000001);
        assertEquals(0.01, MoneyMath.roundCents(0.006), 0.0000001);
        assertEquals(0.00, MoneyMath.roundCents(0.004), 0.0000001);
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MoneyMath.roundCents(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyMath.roundCents(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> MoneyMath.roundCents(Double.NEGATIVE_INFINITY));
    }
}
