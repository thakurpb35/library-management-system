package library.ui;

import javax.swing.JOptionPane;
import java.awt.Component;

/**
 * Every pop-up question or error goes through here. The automated UI check swaps
 * the handler for one that answers by itself, so no window blocks the test.
 */
public final class Dialogs {

    public interface Handler {
        void error(Component parent, String message);

        boolean confirm(Component parent, String title, String message, String yesLabel);
    }

    private static final class SwingHandler implements Handler {
        @Override
        public void error(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, Ui.html(message), "Cannot do that", JOptionPane.WARNING_MESSAGE);
        }

        @Override
        public boolean confirm(Component parent, String title, String message, String yesLabel) {
            Object[] options = {yesLabel, "Cancel"};
            int choice = JOptionPane.showOptionDialog(parent, Ui.html(message), title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            return choice == 0;
        }
    }

    private static Handler handler = new SwingHandler();

    private Dialogs() {
    }

    public static void setHandler(Handler newHandler) {
        handler = newHandler;
    }

    static void error(Component parent, String message) {
        handler.error(parent, message);
    }

    static boolean confirm(Component parent, String title, String message, String yesLabel) {
        return handler.confirm(parent, title, message, yesLabel);
    }
}
