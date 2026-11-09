package library.dao;

import library.model.Stats;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** The totals shown on the dashboard. */
public final class StatsDao {

    private StatsDao() {
    }

    public static Stats load(Connection c, LocalDate today) throws SQLException {
        String sql = """
                SELECT (SELECT COUNT(*) FROM books),
                       (SELECT COALESCE(SUM(total_copies), 0) FROM books),
                       (SELECT COUNT(*) FROM members WHERE active = TRUE),
                       (SELECT COUNT(*) FROM issue_records WHERE return_date IS NULL),
                       (SELECT COUNT(*) FROM issue_records WHERE return_date IS NULL AND due_date < ?),
                       (SELECT COALESCE(SUM(fine_amount), 0) FROM issue_records WHERE return_date IS NOT NULL)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int titles = rs.getInt(1);
                int copies = rs.getInt(2);
                int members = rs.getInt(3);
                int onLoan = rs.getInt(4);
                int overdue = rs.getInt(5);
                BigDecimal fines = rs.getBigDecimal(6);
                return new Stats(titles, copies, copies - onLoan, members, onLoan, overdue, fines);
            }
        }
    }
}
