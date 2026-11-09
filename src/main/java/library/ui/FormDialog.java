package library.ui;

import library.service.LibraryException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * A pop-up form with Save and Cancel. If saving fails (for example a missing field),
 * the problem is shown in red inside the form and the form stays open.
 */
abstract class FormDialog extends JDialog {

    private final JLabel problemLabel = new JLabel(" ");
    private boolean saved;

    FormDialog(Window owner, String title) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
    }

    protected void build(JPanel form, String okText) {
        form.setBorder(BorderFactory.createEmptyBorder(18, 20, 8, 20));
        problemLabel.setForeground(Theme.RED);
        problemLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        problemLabel.setName("dialog.problem");
        JButton ok = new JButton(okText);
        ok.setName("dialog.ok");
        JButton cancel = new JButton("Cancel");
        cancel.setName("dialog.cancel");
        ok.addActionListener(e -> trySave());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        buttons.add(cancel);
        buttons.add(ok);
        JPanel south = new JPanel(new BorderLayout());
        south.add(problemLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setResizable(false);
    }

    /** Saves the form. Returns a problem to show to the user, or null when it worked. */
    protected abstract String save();

    private void trySave() {
        String problem;
        try {
            problem = save();
        } catch (LibraryException e) {
            problem = e.getMessage();
        }
        if (problem == null) {
            saved = true;
            dispose();
        } else {
            problemLabel.setText(Ui.html(problem));
            pack();
        }
    }

    /** Shows the form and waits. Returns true if it was saved. */
    boolean showDialog() {
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }
}
