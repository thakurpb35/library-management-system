package library.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The database design (third normal form): authors, categories, books, members and
 * issue_records. Authors and categories are stored once and referenced by id, a book
 * never stores a count of available copies (it is worked out from the issue records
 * that have not been returned, so it can never drift), and every link is a foreign key.
 */
final class Schema {

    private Schema() {
    }

    private static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS authors (
                author_id   INT AUTO_INCREMENT PRIMARY KEY,
                name        VARCHAR(120) NOT NULL UNIQUE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS categories (
                category_id INT AUTO_INCREMENT PRIMARY KEY,
                name        VARCHAR(60) NOT NULL UNIQUE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS books (
                book_id        INT AUTO_INCREMENT PRIMARY KEY,
                isbn           VARCHAR(20) NULL UNIQUE,
                title          VARCHAR(200) NOT NULL,
                author_id      INT NOT NULL,
                category_id    INT NOT NULL,
                published_year SMALLINT NULL,
                total_copies   INT NOT NULL DEFAULT 1,
                CONSTRAINT fk_books_author   FOREIGN KEY (author_id)   REFERENCES authors (author_id),
                CONSTRAINT fk_books_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
                CONSTRAINT chk_books_copies  CHECK (total_copies >= 1),
                INDEX idx_books_title (title)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS members (
                member_id INT AUTO_INCREMENT PRIMARY KEY,
                name      VARCHAR(100) NOT NULL,
                phone     VARCHAR(15) NOT NULL,
                email     VARCHAR(120) NULL UNIQUE,
                joined_on DATE NOT NULL,
                active    BOOLEAN NOT NULL DEFAULT TRUE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS issue_records (
                issue_id    INT AUTO_INCREMENT PRIMARY KEY,
                book_id     INT NOT NULL,
                member_id   INT NOT NULL,
                issue_date  DATE NOT NULL,
                due_date    DATE NOT NULL,
                return_date DATE NULL,
                fine_amount DECIMAL(8,2) NOT NULL DEFAULT 0.00,
                CONSTRAINT fk_issue_book   FOREIGN KEY (book_id)   REFERENCES books (book_id),
                CONSTRAINT fk_issue_member FOREIGN KEY (member_id) REFERENCES members (member_id),
                CONSTRAINT chk_issue_dates CHECK (due_date >= issue_date),
                INDEX idx_issue_open (return_date, due_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    /** Creates any table that does not exist yet. Safe to run on every start. */
    static void create(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String sql : STATEMENTS) {
                st.execute(sql);
            }
        }
    }
}
