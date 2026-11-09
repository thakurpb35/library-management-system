package library.model;

/** Which part of a book the search box looks at. */
public enum BookField {
    ANY("Any field"),
    TITLE("Title"),
    AUTHOR("Author"),
    ISBN("ISBN"),
    CATEGORY("Category");

    private final String label;

    BookField(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
