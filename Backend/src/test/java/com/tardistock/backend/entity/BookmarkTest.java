package com.tardistock.backend.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BookmarkTest {

    @Test
    void rejectsPriceOutsideDecimalPrecision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Bookmark(new Member(), "AAPL", 1e308)
        );
    }
}
