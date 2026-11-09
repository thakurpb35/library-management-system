package library.model;

/** What the Add/Edit book form sends to the service. {@code id == 0} means a new book. */
public record BookInput(int id, String title, String author, String isbn, String category,
                        Integer year, int totalCopies) {
}
