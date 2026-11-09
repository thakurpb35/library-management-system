package library.dao;

/** Small helpers shared by the DAO classes. */
final class Sql {

    private Sql() {
    }

    /**
     * Prepares search text for a LIKE query written with {@code ESCAPE '!'}, so that a
     * typed % or _ is searched for literally instead of acting as a wildcard.
     */
    static String contains(String text) {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
