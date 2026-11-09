package library.dao;

import library.model.Issue;
import library.model.IssueFilter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the issue_records table (books given to members and taken back). */
public final class IssueDao {

    private IssueDao() {
    }

    private static final String SELECT = """
            SELECT i.issue_id, i.book_id, b.title, i.member_id, m.name,
                   i.issue_date, i.due_date, i.return_date, i.fine_amount
            FROM issue_records i
            JOIN books b ON b.book_id = i.book_id
            JOIN members m ON m.member_id = i.member_id
            """;

    /** What is locked by {@link #lock}. */
    public record Lock(LocalDate dueOn, LocalDate returnedOn) {
    }

    /** Lists records, books still out first (earliest due date first), then returned ones, newest first. */
    public static List<Issue> list(Connection c, IssueFilter filter, Integer memberId, LocalDate today)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        switch (filter) {
            case ON_LOAN -> sql.append(" AND i.return_date IS NULL");
            case OVERDUE -> {
                sql.append(" AND i.return_date IS NULL AND i.due_date < ?");
                params.add(today);
            }
            case RETURNED -> sql.append(" AND i.return_date IS NOT NULL");
            case ALL -> { }
        }
        if (memberId != null) {
            sql.append(" AND i.member_id = ?");
            params.add(memberId);
        }
        sql.append(" ORDER BY (i.return_date IS NOT NULL),"
                + " CASE WHEN i.return_date IS NULL THEN i.due_date END ASC,"
                + " i.return_date DESC, i.issue_id DESC");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Issue> issues = new ArrayList<>();
                while (rs.next()) {
                    issues.add(map(rs));
                }
                return issues;
            }
        }
    }

    public static Issue find(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE i.issue_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Locks one issue record until the transaction ends, so a book cannot be returned twice. */
    public static Lock lock(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT due_date, return_date FROM issue_records WHERE issue_id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new Lock(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class))
                        : null;
            }
        }
    }

    public static int insert(Connection c, int bookId, int memberId, LocalDate issuedOn, LocalDate dueOn)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO issue_records (book_id, member_id, issue_date, due_date) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            ps.setObject(3, issuedOn);
            ps.setObject(4, dueOn);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static void markReturned(Connection c, int id, LocalDate returnedOn, BigDecimal fine)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE issue_records SET return_date = ?, fine_amount = ? WHERE issue_id = ?")) {
            ps.setObject(1, returnedOn);
            ps.setBigDecimal(2, fine);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    public static int countOpenForMember(Connection c, int memberId) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM issue_records WHERE member_id = ? AND return_date IS NULL", memberId);
    }

    public static int countOpenForBook(Connection c, int bookId) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM issue_records WHERE book_id = ? AND return_date IS NULL", bookId);
    }

    public static int countAllForBook(Connection c, int bookId) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM issue_records WHERE book_id = ?", bookId);
    }

    public static int countAllForMember(Connection c, int memberId) throws SQLException {
        return count(c, "SELECT COUNT(*) FROM issue_records WHERE member_id = ?", memberId);
    }

    /** True if this member already has a copy of this book that has not been returned. */
    public static boolean hasOpen(Connection c, int bookId, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM issue_records WHERE book_id = ? AND member_id = ? AND return_date IS NULL")) {
            ps.setInt(1, bookId);
            ps.setInt(2, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static int count(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Issue map(ResultSet rs) throws SQLException {
        return new Issue(rs.getInt("issue_id"), rs.getInt("book_id"), rs.getString("title"),
                rs.getInt("member_id"), rs.getString("name"),
                rs.getObject("issue_date", LocalDate.class), rs.getObject("due_date", LocalDate.class),
                rs.getObject("return_date", LocalDate.class), rs.getBigDecimal("fine_amount"));
    }
}
