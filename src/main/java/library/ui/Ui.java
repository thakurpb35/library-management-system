package library.ui;

import library.model.IssueStatus;
import library.service.LibraryException;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/** Small helpers shared by the screens. */
final class Ui {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private Ui() {
    }

    /** A table with the app's look: sortable columns, formatted dates, money and statuses. */
    static <T> JTable table(ListTableModel<T> model, String currency) {
        JTable t = new JTable(model);
        t.setRowHeight(28);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
        t.setGridColor(Theme.GRID);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        t.setDefaultRenderer(LocalDate.class,
                new FormatRenderer(v -> DATE.format((LocalDate) v), SwingConstants.LEFT, false));
        t.setDefaultRenderer(BigDecimal.class,
                new FormatRenderer(v -> currency + ((BigDecimal) v).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        SwingConstants.RIGHT, false));
        t.setDefaultRenderer(Integer.class, new FormatRenderer(Object::toString, SwingConstants.CENTER, false));
        t.setDefaultRenderer(IssueStatus.class, new StatusRenderer());
        return t;
    }

    /** Shows 0 in red; used for the "Available" column. */
    static FormatRenderer zeroInRed() {
        return new FormatRenderer(Object::toString, SwingConstants.CENTER, true);
    }

    static JScrollPane scroll(JTable table) {
        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(BorderFactory.createLineBorder(Theme.GRID));
        pane.getViewport().setBackground(Color.WHITE);
        return pane;
    }

    /** The object in the selected table row, or null if nothing is selected. */
    static <T> T selected(JTable table, ListTableModel<T> model) {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    static void onChange(JTextComponent field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    /** A whole number typed by the user, or null if the box is empty or not a number. */
    static Integer parseId(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    /** Wraps plain text in HTML so a label or dialog breaks long lines instead of growing very wide. */
    static String html(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<html><body style='width: 320px'>" + safe + "</body></html>";
    }

    /** Runs a screen action and shows any problem as a pop-up instead of crashing the screen. */
    static void guard(Component parent, Runnable action) {
        try {
            action.run();
        } catch (LibraryException e) {
            Dialogs.error(parent, e.getMessage());
        }
    }

    /** Adds a "label: field" row to a form that uses GridBagLayout. */
    static void formRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.LINE_END;
        l.insets = new Insets(5, 0, 5, 12);
        form.add(new JLabel(label), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(5, 0, 5, 0);
        form.add(field, f);
    }

    /** Draws a value as text, centred/right/left aligned; empty values show as a dash. */
    static final class FormatRenderer extends DefaultTableCellRenderer {
        private final Function<Object, String> format;
        private final int alignment;
        private final boolean zeroInRed;

        FormatRenderer(Function<Object, String> format, int alignment, boolean zeroInRed) {
            this.format = format;
            this.alignment = alignment;
            this.zeroInRed = zeroInRed;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "–" : format.apply(value),
                    selected, focus, row, column);
            setHorizontalAlignment(alignment);
            setFont(table.getFont());
            if (!selected) {
                boolean zero = zeroInRed && Integer.valueOf(0).equals(value);
                setForeground(zero ? Theme.RED : value == null ? Theme.MUTED : table.getForeground());
                if (zero) {
                    setFont(Theme.bold(table.getFont(), table.getFont().getSize2D()));
                }
            }
            return this;
        }
    }

    /** On loan in blue, overdue in bold red, returned in grey. */
    static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setFont(table.getFont());
            if (!selected) {
                if (value == IssueStatus.OVERDUE) {
                    setForeground(Theme.RED);
                    setFont(Theme.bold(table.getFont(), table.getFont().getSize2D()));
                } else if (value == IssueStatus.RETURNED) {
                    setForeground(Theme.MUTED);
                } else {
                    setForeground(Theme.BLUE);
                }
            }
            return this;
        }
    }
}
