package library.ui;

import library.model.Book;
import library.model.BookField;
import library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** Search, add, edit and delete books. */
final class BooksPanel extends JPanel implements Refreshable {

    private final MainFrame owner;
    private final LibraryService service;
    private final JTextField search = new JTextField(26);
    private final JComboBox<BookField> field = new JComboBox<>(BookField.values());
    private final JLabel count = Ui.muted(" ");
    private final ListTableModel<Book> model;
    private final JTable table;

    BooksPanel(MainFrame owner) {
        super(new BorderLayout(0, 10));
        this.owner = owner;
        this.service = owner.service();
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        search.setName("books.search");
        field.setName("books.field");
        JButton clear = new JButton("Clear");
        clear.setName("books.clear");
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        bar.add(new JLabel("Search"));
        bar.add(search);
        bar.add(new JLabel("in"));
        bar.add(field);
        bar.add(clear);
        add(bar, BorderLayout.NORTH);

        model = new ListTableModel<Book>()
                .col("ID", Integer.class, Book::id)
                .col("Title", String.class, Book::title)
                .col("Author", String.class, Book::author)
                .col("Category", String.class, Book::category)
                .col("ISBN", String.class, Book::isbn)
                .col("Year", Integer.class, Book::year)
                .col("Copies", Integer.class, Book::totalCopies)
                .col("Available", Integer.class, Book::availableCopies);
        table = Ui.table(model, service.config().currency());
        table.setName("books.table");
        int[] widths = {50, 260, 180, 120, 130, 60, 65, 85};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(7).setCellRenderer(Ui.zeroInRed());
        add(Ui.scroll(table), BorderLayout.CENTER);

        JButton add = new JButton("Add book");
        add.setName("books.add");
        JButton edit = new JButton("Edit selected");
        edit.setName("books.edit");
        JButton delete = new JButton("Delete selected");
        delete.setName("books.delete");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(delete);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(buttons, BorderLayout.WEST);
        south.add(count, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        Ui.onChange(search, () -> Ui.guard(this, this::refresh));
        field.addActionListener(e -> Ui.guard(this, this::refresh));
        clear.addActionListener(e -> {
            search.setText("");
            field.setSelectedItem(BookField.ANY);
        });
        add.addActionListener(e -> addBook());
        edit.addActionListener(e -> editSelected());
        delete.addActionListener(e -> deleteSelected());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });
    }

    @Override
    public void refresh() {
        List<Book> books = service.searchBooks(search.getText(), (BookField) field.getSelectedItem());
        model.setRows(books);
        count.setText(books.size() + (books.size() == 1 ? " book" : " books"));
    }

    private void addBook() {
        if (BookDialog.open(owner, null)) {
            Ui.guard(this, this::refresh);
            owner.status("Book added.");
        }
    }

    private void editSelected() {
        Book book = Ui.selected(table, model);
        if (book == null) {
            Dialogs.error(owner, "Select a book in the table first.");
            return;
        }
        if (BookDialog.open(owner, book)) {
            Ui.guard(this, this::refresh);
            owner.status("Book \"" + book.title() + "\" updated.");
        }
    }

    private void deleteSelected() {
        Book book = Ui.selected(table, model);
        if (book == null) {
            Dialogs.error(owner, "Select a book in the table first.");
            return;
        }
        if (!Dialogs.confirm(owner, "Delete book", "Delete \"" + book.title() + "\"?\nThis cannot be undone.", "Delete")) {
            return;
        }
        Ui.guard(this, () -> {
            service.deleteBook(book.id());
            refresh();
            owner.status("Book \"" + book.title() + "\" deleted.");
        });
    }
}
