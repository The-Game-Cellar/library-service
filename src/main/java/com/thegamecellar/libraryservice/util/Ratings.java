package com.thegamecellar.libraryservice.util;

import java.math.BigDecimal;

public final class Ratings {

    private Ratings() {
    }

    // A rating moves in half steps (6.5 is a rating, 6.3 is not); the range is the request's
    // @DecimalMin / @DecimalMax. Null passes, since the rating is optional.
    public static boolean onHalfStep(BigDecimal rating) {
        if (rating == null) return true;
        return rating.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() <= 0;
    }
}
