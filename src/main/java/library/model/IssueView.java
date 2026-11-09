package library.model;

import java.math.BigDecimal;

/**
 * An issue record together with what is true about it today:
 * its status, how many days late it is (or was) and the fine
 * (the amount collected if returned, the amount owed so far if not).
 */
public record IssueView(Issue issue, IssueStatus status, int daysLate, BigDecimal fine) {
}
