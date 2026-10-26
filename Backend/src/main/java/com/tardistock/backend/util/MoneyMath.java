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

    public static BigDecimal roundCents(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("금액을 입력해주세요.");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static boolean fitsCents(BigDecimal value) {
        return value != null && roundCents(value).precision() <= MONEY_PRECISION;
    }

    public static BigDecimal requireCents(BigDecimal value) {
        if (!fitsCents(value)) {
            throw new IllegalArgumentException("금액이 DECIMAL(19,2) 허용 범위를 초과합니다.");
        }
        return roundCents(value);
    }
}
