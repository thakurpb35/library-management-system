package library;

import library.config.AppConfig;
import library.db.Database;
import library.service.LibraryService;
import library.ui.ConnectionDialog;
import library.ui.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;

/** Starts the Library Management System. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Library Management System");
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine
        }
        AppConfig config = AppConfig.load();
        Database db = connect(config);
        if (db == null) {
            System.exit(0);
            return;
        }
        LibraryService service = new LibraryService(db, config, Clock.systemDefaultZone());
        String info = "Connected to MySQL at " + config.dbHost() + ":" + config.dbPort()
                + "  ·  database \"" + config.dbName() + "\"";
        new MainFrame(service, info).setVisible(true);
    }

    /**
     * Connects to MySQL and prepares the database. If that fails (wrong password, server not
     * running...) the user is asked for the details and it is tried again.
     */
    private static Database connect(AppConfig config) {
        String problem = null;
        boolean askedUser = false;
        while (true) {
            if (problem != null) {
                if (!ConnectionDialog.ask(config, problem)) {
                    return null;
                }
                askedUser = true;
            }
            try {
                Database db = new Database(config);
                db.initialize(LocalDate.now());
                if (askedUser) {
                    saveSettings(config);
                }
                return db;
            } catch (SQLException | RuntimeException e) {
                problem = explain(e);
            }
        }
    }

    private static void saveSettings(AppConfig config) {
        try {
            config.save();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Connected, but the settings could not be saved to library.properties:\n" + e.getMessage(),
                    "Library Management System", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** A short explanation of why connecting failed, in plain words. */
    private static String explain(Exception e) {
        String message = String.valueOf(e.getMessage());
        String lower = message.toLowerCase();
        if (lower.contains("access denied")) {
            return "MySQL did not accept that user name or password.";
        }
        if (lower.contains("no suitable driver")) {
            return "The MySQL driver was not found. Start the app with  ./gradlew run  (see the README).";
        }
        if (lower.contains("communications link failure") || lower.contains("connection refused")
                || lower.contains("could not connect") || lower.contains("timed out")
                || lower.contains("unknown host")) {
            return "Could not reach MySQL at that host and port. Is the MySQL server running?";
        }
        return "Could not connect: " + (message.length() > 300 ? message.substring(0, 300) + "…" : message);
    }
}
