package library.db;

import library.config.AppConfig;
import library.dao.BookDao;
import library.dao.IssueDao;
import library.dao.MemberDao;
import library.service.FineCalculator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Fills a brand-new database with some books, members and issue records, so the
 * app has something to show on the first start. The dates are relative to today,
 * so there are always a few overdue books on the dashboard.
 */
final class SampleData {

    private SampleData() {
    }

    static void load(Connection c, LocalDate today, AppConfig config) throws SQLException {
        int cleanCode = book(c, "Clean Code", "Robert C. Martin", "9780132350884", "Programming", 2008, 1);
        int effectiveJava = book(c, "Effective Java", "Joshua Bloch", "9780134685991", "Programming", 2018, 2);
        int headFirstJava = book(c, "Head First Java", "Kathy Sierra", "9780596009205", "Programming", 2005, 2);
        book(c, "Introduction to Algorithms", "Thomas H. Cormen", "9780262033848", "Computer Science", 2009, 2);
        book(c, "The C Programming Language", "Brian W. Kernighan", "9780131103627", "Programming", 1988, 2);
        book(c, "Database System Concepts", "Abraham Silberschatz", null, "Computer Science", 2019, 2);
        int alchemist = book(c, "The Alchemist", "Paulo Coelho", "9780061122415", "Fiction", 1988, 2);
        book(c, "Wings of Fire", "A. P. J. Abdul Kalam", null, "Biography", 1999, 3);
        book(c, "The Guide", "R. K. Narayan", null, "Fiction", 1958, 2);
        book(c, "Train to Pakistan", "Khushwant Singh", null, "Fiction", 1956, 2);
        book(c, "The White Tiger", "Aravind Adiga", null, "Fiction", 2008, 2);
        book(c, "Godan", "Munshi Premchand", null, "Fiction", 1936, 2);
        int atomicHabits = book(c, "Atomic Habits", "James Clear", "9780735211292", "Self-help", 2018, 3);
        book(c, "Ikigai", "Héctor García", null, "Self-help", 2016, 2);
        book(c, "A Brief History of Time", "Stephen Hawking", "9780553380163", "Science", 1988, 2);

        int aarav = member(c, "Aarav Sharma", "9876500001", "aarav.sharma@example.com", today.minusDays(400), true);
        int simran = member(c, "Simran Kaur", "9876500002", "simran.kaur@example.com", today.minusDays(300), true);
        int rohan = member(c, "Rohan Verma", "9876500003", "rohan.verma@example.com", today.minusDays(200), true);
        int priya = member(c, "Priya Singh", "9876500004", "priya.singh@example.com", today.minusDays(120), true);
        int harpreet = member(c, "Harpreet Gill", "9876500005", "harpreet.gill@example.com", today.minusDays(60), true);
        member(c, "Neha Joshi", "9876500006", null, today.minusDays(20), true);
        member(c, "Karan Mehta", "9876500007", "karan.mehta@example.com", today.minusDays(500), false);

        int days = config.loanDays();
        BigDecimal perDay = config.finePerDay();

        // Two books that are overdue right now, and one that is out but not yet due.
        issue(c, cleanCode, aarav, today.minusDays(days + 6), days, null, perDay);
        issue(c, effectiveJava, simran, today.minusDays(days + 11), days, null, perDay);
        issue(c, atomicHabits, rohan, today.minusDays(3), days, null, perDay);
        // Two books that came back: one late (so a fine was collected), one on time.
        issue(c, alchemist, priya, today.minusDays(days + 16), days, today.minusDays(10), perDay);
        issue(c, headFirstJava, harpreet, today.minusDays(days + 26), days, today.minusDays(27), perDay);
    }

    private static int book(Connection c, String title, String author, String isbn, String category,
                            int year, int copies) throws SQLException {
        return BookDao.insert(c, title, author, isbn, category, year, copies);
    }

    private static int member(Connection c, String name, String phone, String email,
                              LocalDate joined, boolean active) throws SQLException {
        return MemberDao.insert(c, name, phone, email, joined, active);
    }

    private static void issue(Connection c, int bookId, int memberId, LocalDate issuedOn, int loanDays,
                              LocalDate returnedOn, BigDecimal perDay) throws SQLException {
        LocalDate dueOn = issuedOn.plusDays(loanDays);
        int id = IssueDao.insert(c, bookId, memberId, issuedOn, dueOn);
        if (returnedOn != null) {
            IssueDao.markReturned(c, id, returnedOn, FineCalculator.fine(dueOn, returnedOn, perDay));
        }
    }
}
