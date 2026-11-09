package library.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** The late-return fine rule: a fixed amount for every full day after the due date. */
public final class FineCalculator {

    private FineCalculator() {
    }

    /** Whole days after the due date; 0 when the book is on time or early. */
    public static int daysLate(LocalDate dueOn, LocalDate returnedOn) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(dueOn, returnedOn));
    }

    public static BigDecimal fine(int daysLate, BigDecimal perDay) {
        return perDay.multiply(BigDecimal.valueOf(Math.max(0, daysLate))).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal fine(LocalDate dueOn, LocalDate returnedOn, BigDecimal perDay) {
        return fine(daysLate(dueOn, returnedOn), perDay);
    }
}
