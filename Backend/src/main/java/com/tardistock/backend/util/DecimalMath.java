package com.tardistock.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalMath {

    private DecimalMath() {}

    public static boolean fits(
            double value,
            int precision,
            int scale) {
        if (!Double.isFinite(value)) {
            return false;
        }
        if (precision <= 0 || scale < 0 || scale > precision) {
            throw new IllegalArgumentException(
                    "DECIMAL precision/scale 설정이 올바르지 않습니다."
            );
        }

        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .precision() <= precision;
    }
}
