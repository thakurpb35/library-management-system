package library.ui;

import library.model.IssueFilter;
import library.model.IssueView;
import library.model.Stats;
import library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;

/** Totals at a glance, plus the list of books that are overdue today. */
final class DashboardPanel extends JPanel implements Refreshable {

    private final LibraryService service;
    private final StatCard titles = new StatCard("Book titles", Theme.BLUE);
    private final StatCard available = new StatCard("Copies available", Theme.GREEN);
    private final StatCard members = new StatCard("Active members", Theme.PURPLE);
    private final StatCard onLoan = new StatCard("Books on loan", Theme.AMBER);
    private final StatCard overdue = new StatCard("Overdue", Theme.RED);
    private final StatCard fines = new StatCard("Fines collected", Theme.TEAL);
    private final JLabel overdueNote = new JLabel(" ");
    private final ListTableModel<IssueView> model;
    private final JTable table;

    DashboardPanel(MainFrame owner) {
        super(new BorderLayout(0, 16));
        this.service = owner.service();
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        titles.setName("card.titles");
        available.setName("card.available");
        members.setName("card.members");
        onLoan.setName("card.onLoan");
        overdue.setName("card.overdue");
        fines.setName("card.fines");

        JPanel cards = new JPanel(new GridLayout(1, 6, 12, 0));
        cards.setOpaque(false);
        cards.add(titles);
        cards.add(available);
        cards.add(members);
        cards.add(onLoan);
        cards.add(overdue);
        cards.add(fines);
        add(cards, BorderLayout.NORTH);

        model = new ListTableModel<IssueView>()
                .col("Member", String.class, v -> v.issue().memberName() + "  (ID " + v.issue().memberId() + ")")
                .col("Book", String.class, v -> v.issue().bookTitle())
                .col("Due date", java.time.LocalDate.class, v -> v.issue().dueOn())
                .col("Days late", Integer.class, IssueView::daysLate)
                .col("Fine so far", BigDecimal.class, IssueView::fine);
        table = Ui.table(model, service.config().currency());
        table.setName("dashboard.overdue");
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(300);

        JLabel heading = new JLabel("Overdue books");
        heading.setFont(Theme.bold(heading.getFont(), 16f));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> Ui.guard(this, this::refresh));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading, BorderLayout.WEST);
        top.add(refresh, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        bottom.add(top, BorderLayout.NORTH);
        bottom.add(Ui.scroll(table), BorderLayout.CENTER);
        overdueNote.setForeground(Theme.MUTED);
        overdueNote.setName("dashboard.note");
        bottom.add(overdueNote, BorderLayout.SOUTH);
        add(bottom, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Stats s = service.stats();
        titles.setValue(String.valueOf(s.titles()));
        available.setValue(s.availableCopies() + " / " + s.totalCopies());
        members.setValue(String.valueOf(s.activeMembers()));
        onLoan.setValue(String.valueOf(s.onLoan()));
        overdue.setValue(String.valueOf(s.overdue()));
        fines.setValue(service.config().money(s.finesCollected()));

        List<IssueView> rows = service.listIssues(IssueFilter.OVERDUE, null);
        model.setRows(rows);
        String rule = "Fine: " + service.config().money(service.config().finePerDay()) + " for every day after the due date.";
        overdueNote.setText(rows.isEmpty() ? "Nothing is overdue right now. " + rule
                : rows.size() + (rows.size() == 1 ? " book is" : " books are") + " overdue. " + rule);
    }
}
