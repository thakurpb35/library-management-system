package library.ui;

import library.model.Book;
import library.model.IssueFilter;
import library.model.IssueStatus;
import library.model.IssueView;
import library.model.Member;
import library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Give a book to a member, take it back, and look through the issue records. */
final class LoansPanel extends JPanel implements Refreshable {

    private final MainFrame owner;
    private final LibraryService service;
    private final JTextField memberField = new JTextField(8);
    private final JTextField bookField = new JTextField(8);
    private final JLabel memberInfo = new JLabel(" ");
    private final JLabel bookInfo = new JLabel(" ");
    private final JComboBox<IssueFilter> filter = new JComboBox<>(IssueFilter.values());
    private final JTextField filterMember = new JTextField(6);
    private final JLabel count = Ui.muted(" ");
    private final ListTableModel<IssueView> model;
    private final JTable table;

    LoansPanel(MainFrame owner) {
        super(new BorderLayout(0, 14));
        this.owner = owner;
        this.service = owner.service();
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        memberField.setName("issue.memberId");
        bookField.setName("issue.bookId");
        memberInfo.setName("issue.memberInfo");
        bookInfo.setName("issue.bookInfo");
        filter.setName("issue.filter");
        filterMember.setName("issue.filterMember");

        add(issueCard(), BorderLayout.NORTH);

        model = new ListTableModel<IssueView>()
                .col("ID", Integer.class, v -> v.issue().id())
                .col("Book", String.class, v -> v.issue().bookTitle())
                .col("Member", String.class, v -> v.issue().memberName() + "  (ID " + v.issue().memberId() + ")")
                .col("Issued", LocalDate.class, v -> v.issue().issuedOn())
                .col("Due", LocalDate.class, v -> v.issue().dueOn())
                .col("Returned", LocalDate.class, v -> v.issue().returnedOn())
                .col("Status", IssueStatus.class, IssueView::status)
                .col("Days late", Integer.class, IssueView::daysLate)
                .col("Fine", BigDecimal.class, IssueView::fine);
        table = Ui.table(model, service.config().currency());
        table.setName("issue.table");
        int[] widths = {45, 230, 200, 100, 100, 100, 85, 70, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel records = new JPanel(new BorderLayout(0, 8));
        records.setOpaque(false);
        records.add(recordsBar(), BorderLayout.NORTH);
        records.add(Ui.scroll(table), BorderLayout.CENTER);
        records.add(count, BorderLayout.SOUTH);
        add(records, BorderLayout.CENTER);

        Ui.onChange(memberField, () -> Ui.guard(this, this::showMemberInfo));
        Ui.onChange(bookField, () -> Ui.guard(this, this::showBookInfo));
        Ui.onChange(filterMember, () -> Ui.guard(this, this::refresh));
        filter.addActionListener(e -> Ui.guard(this, this::refresh));
        showMemberInfo();
        showBookInfo();
    }

    private JPanel issueCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GRID),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));

        JLabel heading = new JLabel("Issue a book");
        heading.setFont(Theme.bold(heading.getFont(), 16f));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 0, 8, 0);
        card.add(heading, c);

        addIssueRow(card, 1, "Member ID", memberField, memberInfo);
        addIssueRow(card, 2, "Book ID", bookField, bookInfo);

        JButton issue = new JButton("Issue book");
        issue.setName("issue.button");
        issue.addActionListener(e -> issueBook());
        submitOnEnter(issue);
        GridBagConstraints b = new GridBagConstraints();
        b.gridx = 1;
        b.gridy = 3;
        b.anchor = GridBagConstraints.LINE_START;
        b.insets = new Insets(8, 0, 0, 0);
        card.add(issue, b);

        String rules = "Loan period " + service.config().loanDays() + " days  ·  fine "
                + service.config().money(service.config().finePerDay()) + " per day late  ·  limit "
                + service.config().maxBooksPerMember() + " books per member";
        GridBagConstraints r = new GridBagConstraints();
        r.gridx = 2;
        r.gridy = 3;
        r.anchor = GridBagConstraints.LINE_START;
        r.insets = new Insets(8, 14, 0, 0);
        card.add(Ui.muted(rules), r);
        return card;
    }

    /** Pressing Enter in either ID box issues the book. */
    private void submitOnEnter(JButton issue) {
        memberField.addActionListener(e -> issue.doClick());
        bookField.addActionListener(e -> issue.doClick());
    }

    private static void addIssueRow(JPanel card, int row, String label, JTextField field, JLabel info) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.LINE_END;
        l.insets = new Insets(4, 0, 4, 10);
        card.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.anchor = GridBagConstraints.LINE_START;
        f.insets = new Insets(4, 0, 4, 0);
        card.add(field, f);
        GridBagConstraints i = new GridBagConstraints();
        i.gridx = 2;
        i.gridy = row;
        i.weightx = 1;
        i.fill = GridBagConstraints.HORIZONTAL;
        i.insets = new Insets(4, 14, 4, 0);
        card.add(info, i);
    }

    private JPanel recordsBar() {
        JLabel heading = new JLabel("Issue records");
        heading.setFont(Theme.bold(heading.getFont(), 16f));
        JButton clear = new JButton("Clear");
        clear.setName("issue.clearFilter");
        clear.addActionListener(e -> filterMember.setText(""));
        JButton returnButton = new JButton("Return selected book");
        returnButton.setName("return.button");
        returnButton.addActionListener(e -> returnSelected());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(heading);
        left.add(new JLabel("   Show"));
        left.add(filter);
        left.add(new JLabel("Member ID"));
        left.add(filterMember);
        left.add(clear);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(left, BorderLayout.WEST);
        bar.add(returnButton, BorderLayout.EAST);
        return bar;
    }

    @Override
    public void refresh() {
        List<IssueView> rows = service.listIssues((IssueFilter) filter.getSelectedItem(),
                Ui.parseId(filterMember.getText()));
        model.setRows(rows);
        count.setText(rows.size() + (rows.size() == 1 ? " record" : " records"));
        showMemberInfo();
        showBookInfo();
    }

    private void showMemberInfo() {
        String text = memberField.getText().trim();
        if (text.isEmpty()) {
            setInfo(memberInfo, "Type the member's ID (see the Members tab)", false);
            return;
        }
        Integer id = Ui.parseId(text);
        if (id == null) {
            setInfo(memberInfo, "The ID must be a number", true);
            return;
        }
        Member m = service.getMember(id);
        if (m == null) {
            setInfo(memberInfo, "No member with ID " + id, true);
        } else if (!m.active()) {
            setInfo(memberInfo, m.name() + "  ·  INACTIVE, cannot borrow", true);
        } else {
            setInfo(memberInfo, m.name() + "  ·  has " + m.booksOut() + " of "
                    + service.config().maxBooksPerMember() + " books out", false);
        }
    }

    private void showBookInfo() {
        String text = bookField.getText().trim();
        if (text.isEmpty()) {
            setInfo(bookInfo, "Type the book's ID (see the Books tab)", false);
            return;
        }
        Integer id = Ui.parseId(text);
        if (id == null) {
            setInfo(bookInfo, "The ID must be a number", true);
            return;
        }
        Book b = service.getBook(id);
        if (b == null) {
            setInfo(bookInfo, "No book with ID " + id, true);
        } else {
            setInfo(bookInfo, "\"" + b.title() + "\" by " + b.author() + "  ·  " + b.availableCopies()
                    + " of " + b.totalCopies() + " available", b.availableCopies() == 0);
        }
    }

    private static void setInfo(JLabel label, String text, boolean problem) {
        label.setText(text);
        label.setForeground(problem ? Theme.RED : Theme.MUTED);
    }

    private void issueBook() {
        Integer memberId = Ui.parseId(memberField.getText());
        Integer bookId = Ui.parseId(bookField.getText());
        if (memberId == null || bookId == null) {
            Dialogs.error(owner, "Please type a member ID and a book ID (numbers) first.");
            return;
        }
        Ui.guard(this, () -> {
            IssueView v = service.issueBook(bookId, memberId);
            bookField.setText("");
            refresh();
            owner.status("Issued \"" + v.issue().bookTitle() + "\" to " + v.issue().memberName()
                    + ". Due on " + Ui.DATE.format(v.issue().dueOn()) + ".");
        });
    }

    private void returnSelected() {
        IssueView selected = Ui.selected(table, model);
        if (selected == null) {
            Dialogs.error(owner, "Select a record in the table first.");
            return;
        }
        if (selected.issue().isReturned()) {
            Dialogs.error(owner, "That book was already returned on "
                    + Ui.DATE.format(selected.issue().returnedOn()) + ".");
            return;
        }
        Ui.guard(this, () -> {
            IssueView now = service.previewReturn(selected.issue().id());
            String what = "\"" + now.issue().bookTitle() + "\" from " + now.issue().memberName();
            boolean go;
            if (now.fine().signum() > 0) {
                go = Dialogs.confirm(owner, "Late return",
                        "This book is " + now.daysLate() + (now.daysLate() == 1 ? " day" : " days") + " late.\n"
                                + "Fine to collect: " + service.config().money(now.fine()) + "\n\n"
                                + "Take back " + what + "?", "Collect fine and return");
            } else {
                go = Dialogs.confirm(owner, "Return book", "Take back " + what + "?", "Return book");
            }
            if (!go) {
                return;
            }
            IssueView done = service.returnBook(now.issue().id());
            refresh();
            owner.status("Returned \"" + done.issue().bookTitle() + "\" from " + done.issue().memberName()
                    + (done.fine().signum() > 0 ? ". Fine collected: " + service.config().money(done.fine()) : ". No fine."));
        });
    }
}
