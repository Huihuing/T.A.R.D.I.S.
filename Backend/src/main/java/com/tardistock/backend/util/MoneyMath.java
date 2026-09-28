package com.tardistock.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    private MoneyMath() {}

    public static double roundCents(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("금액은 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
