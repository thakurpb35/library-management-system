package library.model;

import java.math.BigDecimal;

/** The numbers shown on the dashboard. */
public record Stats(int titles, int totalCopies, int availableCopies, int activeMembers,
                    int onLoan, int overdue, BigDecimal finesCollected) {
}
