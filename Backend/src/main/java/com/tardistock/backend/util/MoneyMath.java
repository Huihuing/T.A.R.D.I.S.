package com.tardistock.backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    public static final int MONEY_PRECISION = 19;
    public static final int MONEY_SCALE = 2;

    private MoneyMath() {}

    public static double roundCents(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("금액은 유한한 숫자여야 합니다.");
        }
        return BigDecimal.valueOf(value)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static boolean fitsCents(double value) {
        return DecimalMath.fits(value, MONEY_PRECISION, MONEY_SCALE);
    }
}
