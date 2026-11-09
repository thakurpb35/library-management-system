package library.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the issue records table: a copy of a book given to a member.
 * {@code returnedOn} is null while the book is still out. {@code fineCharged}
 * is the fine collected when the book was returned (0 until then).
 */
public record Issue(int id, int bookId, String bookTitle, int memberId, String memberName,
                    LocalDate issuedOn, LocalDate dueOn, LocalDate returnedOn, BigDecimal fineCharged) {

    public boolean isReturned() {
        return returnedOn != null;
    }
}
