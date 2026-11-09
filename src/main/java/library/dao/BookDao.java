package library.dao;

import library.model.Book;
import library.model.BookField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the books, authors and categories tables with plain JDBC. */
public final class BookDao {

    private BookDao() {
    }

    private static final String SELECT = """
            SELECT b.book_id, b.isbn, b.title, a.name AS author, c.name AS category,
                   b.published_year, b.total_copies,
                   b.total_copies - (SELECT COUNT(*) FROM issue_records i
                                     WHERE i.book_id = b.book_id AND i.return_date IS NULL) AS available
            FROM books b
            JOIN authors a ON a.author_id = b.author_id
            JOIN categories c ON c.category_id = b.category_id
            """;

    /** What is locked by {@link #lock}. */
    public record Lock(String title, int totalCopies) {
    }

    public static List<Book> search(Connection c, String text, BookField field) throws SQLException {
        String t = text == null ? "" : text.trim();
        StringBuilder sql = new StringBuilder(SELECT);
        int params = 0;
        if (!t.isEmpty()) {
            switch (field) {
                case TITLE -> { sql.append(" WHERE b.title LIKE ? ESCAPE '!'"); params = 1; }
                case AUTHOR -> { sql.append(" WHERE a.name LIKE ? ESCAPE '!'"); params = 1; }
                case ISBN -> { sql.append(" WHERE b.isbn LIKE ? ESCAPE '!'"); params = 1; }
                case CATEGORY -> { sql.append(" WHERE c.name LIKE ? ESCAPE '!'"); params = 1; }
                default -> {
                    sql.append(" WHERE (b.title LIKE ? ESCAPE '!' OR a.name LIKE ? ESCAPE '!'"
                            + " OR b.isbn LIKE ? ESCAPE '!' OR c.name LIKE ? ESCAPE '!')");
                    params = 4;
                }
            }
        }
        sql.append(" ORDER BY b.title, b.book_id");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 1; i <= params; i++) {
                ps.setString(i, Sql.contains(t));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> books = new ArrayList<>();
                while (rs.next()) {
                    books.add(map(rs));
                }
                return books;
            }
        }
    }

    public static Book find(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE b.book_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Locks one book row until the transaction ends, so two clerks cannot hand out
     * the last copy at the same moment. Returns null if there is no such book.
     */
    public static Lock lock(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT title, total_copies FROM books WHERE book_id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Lock(rs.getString(1), rs.getInt(2)) : null;
            }
        }
    }

    public static int insert(Connection c, String title, String author, String isbn, String category,
                             Integer year, int copies) throws SQLException {
        int authorId = idFor(c, "authors", "author_id", author);
        int categoryId = idFor(c, "categories", "category_id", category);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO books (isbn, title, author_id, category_id, published_year, total_copies)"
                        + " VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, isbn);
            ps.setString(2, title);
            ps.setInt(3, authorId);
            ps.setInt(4, categoryId);
            setYear(ps, 5, year);
            ps.setInt(6, copies);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static void update(Connection c, int id, String title, String author, String isbn, String category,
                              Integer year, int copies) throws SQLException {
        int authorId = idFor(c, "authors", "author_id", author);
        int categoryId = idFor(c, "categories", "category_id", category);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE books SET isbn = ?, title = ?, author_id = ?, category_id = ?,"
                        + " published_year = ?, total_copies = ? WHERE book_id = ?")) {
            ps.setString(1, isbn);
            ps.setString(2, title);
            ps.setInt(3, authorId);
            ps.setInt(4, categoryId);
            setYear(ps, 5, year);
            ps.setInt(6, copies);
            ps.setInt(7, id);
            ps.executeUpdate();
        }
    }

    public static boolean delete(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static List<String> categories(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM categories ORDER BY name")) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        }
    }

    /** Finds an author or category by name, adding it first if it is new. */
    private static int idFor(Connection c, String table, String idColumn, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + idColumn + " FROM " + table + " WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + table + " (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void setYear(PreparedStatement ps, int index, Integer year) throws SQLException {
        if (year == null) {
            ps.setNull(index, Types.SMALLINT);
        } else {
            ps.setInt(index, year);
        }
    }

    private static Book map(ResultSet rs) throws SQLException {
        int year = rs.getInt("published_year");
        Integer publishedYear = rs.wasNull() ? null : year;
        return new Book(rs.getInt("book_id"), rs.getString("isbn"), rs.getString("title"),
                rs.getString("author"), rs.getString("category"), publishedYear,
                rs.getInt("total_copies"), rs.getInt("available"));
    }
}
