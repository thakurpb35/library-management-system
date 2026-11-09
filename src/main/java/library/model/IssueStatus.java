package library.model;

public enum IssueStatus {
    ON_LOAN("On loan"),
    OVERDUE("Overdue"),
    RETURNED("Returned");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
