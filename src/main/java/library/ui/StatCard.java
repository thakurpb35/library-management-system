package library.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A white rounded card with a coloured edge, a small title and one big number. */
final class StatCard extends JPanel {

    private final Color accent;
    private final JLabel value = new JLabel("–");

    StatCard(String title, Color accent) {
        super(new BorderLayout(0, 4));
        this.accent = accent;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 12));
        JLabel caption = Ui.muted(title);
        caption.setFont(caption.getFont().deriveFont(12f));
        value.setForeground(accent);
        value.setFont(Theme.bold(value.getFont(), 26f));
        add(caption, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
    }

    void setValue(String text) {
        value.setText(text);
    }

    String valueText() {
        return value.getText();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(Theme.GRID);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(accent);
        g2.fillRoundRect(0, 12, 5, getHeight() - 24, 5, 5);
        g2.dispose();
        super.paintComponent(g);
    }
}
