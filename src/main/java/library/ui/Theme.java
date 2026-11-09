package library.ui;

import java.awt.Color;
import java.awt.Font;

/** Colours and fonts shared by all screens. */
final class Theme {

    private Theme() {
    }

    static final Color NAVY = new Color(0x1E3A5F);
    static final Color BACKGROUND = new Color(0xF4F6FA);
    static final Color GRID = new Color(0xE5E7EB);
    static final Color MUTED = new Color(0x6B7280);
    static final Color BLUE = new Color(0x2563EB);
    static final Color GREEN = new Color(0x15803D);
    static final Color PURPLE = new Color(0x7C3AED);
    static final Color AMBER = new Color(0xB45309);
    static final Color RED = new Color(0xDC2626);
    static final Color TEAL = new Color(0x0F766E);

    static Font bold(Font base, float size) {
        return base.deriveFont(Font.BOLD, size);
    }
}
