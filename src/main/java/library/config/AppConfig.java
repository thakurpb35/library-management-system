package library.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Settings for the database connection and the library rules.
 * They are read from library.properties (in the folder the app is started from).
 * Every setting has a sensible default, so the file is optional.
 */
public final class AppConfig {

    public static final Path DEFAULT_FILE = Path.of("library.properties");

    private final Path file;
    private String dbHost = "localhost";
    private int dbPort = 3306;
    private String dbName = "library_db";
    private String dbUser = "root";
    private String dbPassword = "";
    private int loanDays = 14;
    private BigDecimal finePerDay = new BigDecimal("2");
    private int maxBooksPerMember = 3;
    private String currency = "₹";

    private AppConfig(Path file) {
        this.file = file;
    }

    public static AppConfig load() {
        return load(DEFAULT_FILE);
    }

    public static AppConfig load(Path file) {
        AppConfig c = new AppConfig(file);
        if (!Files.isRegularFile(file)) {
            return c;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return c; // unreadable file: just use the defaults
        }
        c.dbHost = text(p, "db.host", c.dbHost);
        c.dbPort = whole(p, "db.port", c.dbPort, 1);
        c.dbName = text(p, "db.name", c.dbName);
        c.dbUser = text(p, "db.user", c.dbUser);
        c.dbPassword = p.getProperty("db.password", c.dbPassword); // not trimmed on purpose
        c.loanDays = whole(p, "loan.days", c.loanDays, 1);
        c.maxBooksPerMember = whole(p, "max.books.per.member", c.maxBooksPerMember, 1);
        c.currency = text(p, "currency", c.currency);
        try {
            BigDecimal fine = new BigDecimal(text(p, "fine.per.day", c.finePerDay.toPlainString()));
            if (fine.signum() >= 0) {
                c.finePerDay = fine;
            }
        } catch (NumberFormatException ignored) {
            // keep the default
        }
        return c;
    }

    /** Saves the current settings so the next start does not ask again. */
    public void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("db.host", dbHost);
        p.setProperty("db.port", String.valueOf(dbPort));
        p.setProperty("db.name", dbName);
        p.setProperty("db.user", dbUser);
        p.setProperty("db.password", dbPassword);
        p.setProperty("loan.days", String.valueOf(loanDays));
        p.setProperty("fine.per.day", finePerDay.toPlainString());
        p.setProperty("max.books.per.member", String.valueOf(maxBooksPerMember));
        p.setProperty("currency", currency);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "Library Management System settings");
        }
    }

    /** JDBC address of the MySQL server; pass null to connect without choosing a database. */
    public String jdbcUrl(String database) {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + (database == null ? "" : database)
                + "?allowPublicKeyRetrieval=true&connectTimeout=5000&characterEncoding=UTF-8";
    }

    /** Formats an amount of money, e.g. "₹12.00". */
    public String money(BigDecimal amount) {
        return currency + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public void setConnection(String host, int port, String user, String password) {
        this.dbHost = host;
        this.dbPort = port;
        this.dbUser = user;
        this.dbPassword = password;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String dbHost() { return dbHost; }
    public int dbPort() { return dbPort; }
    public String dbName() { return dbName; }
    public String dbUser() { return dbUser; }
    public String dbPassword() { return dbPassword; }
    public int loanDays() { return loanDays; }
    public BigDecimal finePerDay() { return finePerDay; }
    public int maxBooksPerMember() { return maxBooksPerMember; }
    public String currency() { return currency; }

    private static String text(Properties p, String key, String fallback) {
        String v = p.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static int whole(Properties p, String key, int fallback, int min) {
        try {
            int v = Integer.parseInt(text(p, key, String.valueOf(fallback)));
            return v >= min ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
