package library.model;

/**
 * A book title with its stock. {@code availableCopies} is worked out from the
 * issue records that have not been returned yet.
 */
public record Book(int id, String isbn, String title, String author, String category,
                   Integer year, int totalCopies, int availableCopies) {

    public int issuedCopies() {
        return totalCopies - availableCopies;
    }
}
