package library.ui;

import library.config.AppConfig;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Asks for the MySQL details when the app cannot connect (wrong password, server not started...). */
public final class ConnectionDialog {

    private ConnectionDialog() {
    }

    /**
     * Shows the connection form. The values are stored in {@code config} when the user presses Connect.
     *
     * @param problem what went wrong the last time, shown at the top of the form
     * @return false if the user chose to quit
     */
    public static boolean ask(AppConfig config, String problem) {
        String message = problem;
        while (true) {
            JTextField host = new JTextField(config.dbHost(), 22);
            JTextField port = new JTextField(String.valueOf(config.dbPort()), 22);
            JTextField user = new JTextField(config.dbUser(), 22);
            JPasswordField password = new JPasswordField(config.dbPassword(), 22);

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints top = new GridBagConstraints();
            top.gridx = 0;
            top.gridy = 0;
            top.gridwidth = 2;
            top.anchor = GridBagConstraints.LINE_START;
            top.insets = new Insets(0, 0, 10, 0);
            JLabel problemLabel = new JLabel(Ui.html(message));
            problemLabel.setForeground(Theme.RED);
            form.add(problemLabel, top);
            Ui.formRow(form, 1, "Host", host);
            Ui.formRow(form, 2, "Port", port);
            Ui.formRow(form, 3, "User", user);
            Ui.formRow(form, 4, "Password", password);
            GridBagConstraints hint = new GridBagConstraints();
            hint.gridx = 0;
            hint.gridy = 5;
            hint.gridwidth = 2;
            hint.anchor = GridBagConstraints.LINE_START;
            hint.insets = new Insets(10, 0, 0, 0);
            form.add(Ui.muted(Ui.html("Use the user name and password you chose when you installed MySQL. "
                    + "The details are saved in library.properties.")), hint);

            Object[] buttons = {"Connect", "Quit"};
            int choice = JOptionPane.showOptionDialog(null, form, "Connect to MySQL",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]);
            if (choice != 0) {
                return false;
            }
            Integer portNumber = Ui.parseId(port.getText());
            if (host.getText().isBlank() || portNumber == null || portNumber < 1 || portNumber > 65535) {
                message = "Please enter a host name and a port number (MySQL normally uses 3306).";
                config.setConnection(host.getText().isBlank() ? config.dbHost() : host.getText().trim(),
                        config.dbPort(), user.getText().trim(), new String(password.getPassword()));
                continue;
            }
            config.setConnection(host.getText().trim(), portNumber, user.getText().trim(),
                    new String(password.getPassword()));
            return true;
        }
    }
}
