package library.ui;

import library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The main window: a header, four tabs and a status line. */
public final class MainFrame extends JFrame {

    private final LibraryService service;
    private final String connectionInfo;
    private final JLabel status = new JLabel();
    private final Timer statusReset;
    private final JTabbedPane tabs = new JTabbedPane();

    public MainFrame(LibraryService service, String connectionInfo) {
        super("Library Management System");
        this.service = service;
        this.connectionInfo = connectionInfo;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1120, 720);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);

        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);

        tabs.setName("tabs");
        tabs.addTab("Dashboard", new DashboardPanel(this));
        tabs.addTab("Books", new BooksPanel(this));
        tabs.addTab("Members", new MembersPanel(this));
        tabs.addTab("Issue & Return", new LoansPanel(this));
        tabs.addChangeListener(e -> Ui.guard(this, () -> ((Refreshable) tabs.getSelectedComponent()).refresh()));
        add(tabs, BorderLayout.CENTER);

        status.setName("status");
        status.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        status.setText(connectionInfo);
        status.setForeground(Theme.MUTED);
        add(status, BorderLayout.SOUTH);
        statusReset = new Timer(10_000, e -> showConnectionInfo());
        statusReset.setRepeats(false);

        Ui.guard(this, () -> ((Refreshable) tabs.getSelectedComponent()).refresh());
    }

    public LibraryService service() {
        return service;
    }

    /** Shows a short "it worked" message at the bottom of the window. */
    void status(String message) {
        status.setForeground(Theme.GREEN);
        status.setText(message);
        statusReset.restart();
    }

    String statusText() {
        return status.getText();
    }

    private void showConnectionInfo() {
        status.setForeground(Theme.MUTED);
        status.setText(connectionInfo);
    }

    JTabbedPane tabs() {
        return tabs;
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));

        JLabel title = new JLabel("Library Management System");
        title.setForeground(Color.WHITE);
        title.setFont(Theme.bold(title.getFont(), 21f));
        JLabel subtitle = new JLabel("Books  ·  Members  ·  Issue & Return  ·  Fines");
        subtitle.setForeground(new Color(0xB8C4D6));
        JPanel left = new JPanel(new BorderLayout(0, 2));
        left.setOpaque(false);
        left.add(title, BorderLayout.NORTH);
        left.add(subtitle, BorderLayout.SOUTH);

        JLabel date = new JLabel(service.today().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)));
        date.setForeground(new Color(0xB8C4D6));

        header.add(left, BorderLayout.WEST);
        header.add(date, BorderLayout.EAST);
        return header;
    }
}
