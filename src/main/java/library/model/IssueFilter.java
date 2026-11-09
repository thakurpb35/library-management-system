package library.model;

/** Which issue records the Issue &amp; Return tab lists. */
public enum IssueFilter {
    ON_LOAN("On loan"),
    OVERDUE("Overdue only"),
    RETURNED("Returned"),
    ALL("All records");

    private final String label;

    IssueFilter(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
