package library.dao;

import library.model.Member;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the members table with plain JDBC. */
public final class MemberDao {

    private MemberDao() {
    }

    private static final String SELECT = """
            SELECT m.member_id, m.name, m.phone, m.email, m.joined_on, m.active,
                   (SELECT COUNT(*) FROM issue_records i
                    WHERE i.member_id = m.member_id AND i.return_date IS NULL) AS books_out
            FROM members m
            """;

    /** What is locked by {@link #lock}. */
    public record Lock(String name, boolean active) {
    }

    /**
     * Searches by member ID (a short number), or by name, email or phone (anything else).
     * An empty search lists everyone.
     */
    public static List<Member> search(Connection c, String text) throws SQLException {
        String t = text == null ? "" : text.trim();
        StringBuilder sql = new StringBuilder(SELECT);
        boolean byId = !t.isEmpty() && t.matches("\\d{1,6}");
        boolean byText = !t.isEmpty() && !byId;
        if (byId) {
            sql.append(" WHERE m.member_id = ?");
        } else if (byText) {
            sql.append(" WHERE (m.name LIKE ? ESCAPE '!' OR m.email LIKE ? ESCAPE '!' OR m.phone LIKE ? ESCAPE '!')");
        }
        sql.append(" ORDER BY m.member_id");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (byId) {
                ps.setInt(1, Integer.parseInt(t));
            } else if (byText) {
                for (int i = 1; i <= 3; i++) {
                    ps.setString(i, Sql.contains(t));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Member> members = new ArrayList<>();
                while (rs.next()) {
                    members.add(map(rs));
                }
                return members;
            }
        }
    }

    public static Member find(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE m.member_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Locks one member row until the transaction ends. Returns null if there is no such member. */
    public static Lock lock(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, active FROM members WHERE member_id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Lock(rs.getString(1), rs.getBoolean(2)) : null;
            }
        }
    }

    public static int insert(Connection c, String name, String phone, String email,
                             LocalDate joinedOn, boolean active) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO members (name, phone, email, joined_on, active) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setObject(4, joinedOn);
            ps.setBoolean(5, active);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static void update(Connection c, int id, String name, String phone, String email,
                              boolean active) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE members SET name = ?, phone = ?, email = ?, active = ? WHERE member_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setBoolean(4, active);
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    public static boolean delete(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE member_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Member map(ResultSet rs) throws SQLException {
        return new Member(rs.getInt("member_id"), rs.getString("name"), rs.getString("phone"),
                rs.getString("email"), rs.getObject("joined_on", LocalDate.class),
                rs.getBoolean("active"), rs.getInt("books_out"));
    }
}
