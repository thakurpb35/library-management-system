package library.ui;

import library.model.Book;
import library.model.BookInput;
import library.service.LibraryService;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.text.ParseException;

/** The Add book / Edit book form. */
final class BookDialog extends FormDialog {

    private final LibraryService service;
    private final Book existing;
    private final JTextField title = new JTextField(28);
    private final JTextField author = new JTextField(28);
    private final JTextField isbn = new JTextField(28);
    private final JComboBox<String> category = new JComboBox<>();
    private final JTextField year = new JTextField(8);
    private final JSpinner copies = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));

    private BookDialog(Window owner, LibraryService service, Book existing) {
        super(owner, existing == null ? "Add a book" : "Edit book #" + existing.id());
        this.service = service;
        this.existing = existing;

        title.setName("book.title");
        author.setName("book.author");
        isbn.setName("book.isbn");
        category.setName("book.category");
        year.setName("book.year");
        copies.setName("book.copies");

        for (String name : service.categories()) {
            category.addItem(name);
        }
        category.setEditable(true);
        category.setSelectedItem("");
        if (existing != null) {
            title.setText(existing.title());
            author.setText(existing.author());
            isbn.setText(existing.isbn() == null ? "" : existing.isbn());
            category.setSelectedItem(existing.category());
            year.setText(existing.year() == null ? "" : String.valueOf(existing.year()));
            copies.setValue(existing.totalCopies());
        }

        JPanel form = new JPanel(new GridBagLayout());
        Ui.formRow(form, 0, "Title *", title);
        Ui.formRow(form, 1, "Author *", author);
        Ui.formRow(form, 2, "ISBN", isbn);
        Ui.formRow(form, 3, "Category *", category);
        Ui.formRow(form, 4, "Year", year);
        Ui.formRow(form, 5, "Copies *", copies);
        build(form, existing == null ? "Add book" : "Save changes");
    }

    /** Opens the form; returns true if a book was saved. */
    static boolean open(MainFrame owner, Book existing) {
        return new BookDialog(owner, owner.service(), existing).showDialog();
    }

    @Override
    protected String save() {
        Integer publishedYear = null;
        String yearText = year.getText().trim();
        if (!yearText.isEmpty()) {
            try {
                publishedYear = Integer.valueOf(yearText);
            } catch (NumberFormatException e) {
                return "The year must be a number, like 2019.";
            }
        }
        try {
            copies.commitEdit();
        } catch (ParseException e) {
            return "Copies must be a whole number.";
        }
        service.saveBook(new BookInput(existing == null ? 0 : existing.id(), title.getText(), author.getText(),
                isbn.getText(), String.valueOf(category.getEditor().getItem()), publishedYear,
                (Integer) copies.getValue()));
        return null;
    }
}
