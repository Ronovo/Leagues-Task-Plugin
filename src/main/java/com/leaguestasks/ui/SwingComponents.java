package com.leaguestasks.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public final class SwingComponents
{
    public static final int PANEL_BORDER_PX = 10;
    private static final int SAFETY_MARGIN_PX = 10;
    public static final int LABEL_WRAP_WIDTH_PX = PluginPanel.PANEL_WIDTH
        - PluginPanel.SCROLLBAR_WIDTH
        - 2 * PluginPanel.BORDER_OFFSET
        - 2 * PANEL_BORDER_PX
        - SAFETY_MARGIN_PX;

    // Matches a JComboBox arrow button so a centered header lines up
    // with the dropdown's centered value (which excludes the arrow).
    public static final int COMBO_ARROW_WIDTH_PX = 21;

    public static final Color SAVED_COLOR = new Color(40, 110, 40);
    public static final Color EMPTY_COLOR = new Color(110, 40, 40);
    /** Brighter than EMPTY_COLOR so error copy stays readable on the dark panel. */
    public static final Color ERROR_COLOR = new Color(220, 85, 85);
    public static final Color ACCENT_COLOR = ColorScheme.BRAND_ORANGE;
    public static final Color WELCOME_COLOR = new Color(40, 90, 150);
    public static final Color ABOUT_COLOR = new Color(100, 45, 130);
    /** Teal — start-menu "Show My List". */
    public static final Color ACTION_COLOR = new Color(35, 115, 115);
    /** Gold — start-menu "Pick Random Task"; distinct from orange/teal/green/blue/purple. */
    public static final Color PICK_COLOR = new Color(145, 105, 30);
    /** Rose — start-menu "Autocomplete Tasks"; distinct from green sync and other menu colors. */
    public static final Color AUTOCOMPLETE_COLOR = new Color(150, 55, 95);
    /** Bronze — start-menu "Tracker App"; distinct from the blue/purple info buttons and gold "Pick Random Task". */
    public static final Color TRACKER_COLOR = new Color(120, 75, 40);
    /** Steel blue — Sync to Website section buttons; distinct from the green "Sync Everything". */
    public static final Color SYNC_COLOR = new Color(40, 95, 150);

    private SwingComponents()
    {
    }

    public static void stylePanel(JPanel panel)
    {
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setOpaque(true);
    }

    /** "#rrggbb" for a colour, for use inside an inline HTML {@code style}. */
    public static String hex(Color color)
    {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    /**
     * A bold, centered, accent-coloured section heading that wraps instead of
     * clipping. The shared look for card titles and step headers.
     */
    public static JLabel headerLabel(String text)
    {
        JLabel header = wrappingCenteredLabel();
        setCenteredHtml(header, "<b>" + TextUtil.escapeHtml(text) + "</b>");
        header.setForeground(ACCENT_COLOR);
        return header;
    }

    public static JButton centeredButton(String text)
    {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        return button;
    }

    public static void colorButton(JButton button, Color color)
    {
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
    }

    public static JButton fullWidthRowButton(String text)
    {
        JButton row = new JButton(text);
        row.setHorizontalAlignment(JButton.CENTER);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMargin(new Insets(3, 8, 3, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    public static JLabel centeredLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, COMBO_ARROW_WIDTH_PX));
        return label;
    }

    public static JLabel wrappingCenteredLabel()
    {
        JLabel label = new JLabel();
        label.setVerticalAlignment(JLabel.TOP);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    public static void setCenteredHtml(JLabel label, String innerHtml)
    {
        label.setText(centeredHtml(innerHtml));
    }

    /**
     * A read-only HTML view that (unlike JLabel) supports mouse-drag text
     * selection/copy and clickable {@code <a href>} links -- clicking one
     * opens it via RuneLite's {@link LinkBrowser}.
     */
    public static JEditorPane wrappingCenteredHtmlPane()
    {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setFont(UIManager.getFont("Label.font"));
        pane.setForeground(Color.WHITE);
        pane.setAlignmentX(Component.CENTER_ALIGNMENT);
        pane.addHyperlinkListener(e ->
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
            {
                LinkBrowser.browse(e.getDescription());
            }
            else if (e.getEventType() == HyperlinkEvent.EventType.ENTERED)
            {
                pane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            else if (e.getEventType() == HyperlinkEvent.EventType.EXITED)
            {
                pane.setCursor(Cursor.getDefaultCursor());
            }
        });
        return pane;
    }

    public static String centeredHtml(String innerHtml)
    {
        return "<html><body style='width: " + LABEL_WRAP_WIDTH_PX + "px; text-align: center; margin: 0'>"
            + innerHtml + "</body></html>";
    }

    public static JComboBox<String> centeredComboBox()
    {
        JComboBox<String> probe = new JComboBox<>();
        int pinnedHeight = Math.max(probe.getPreferredSize().height / 2, 18);

        JComboBox<String> combo = new JComboBox<String>()
        {
            @Override
            public Dimension getPreferredSize()
            {
                return new Dimension(super.getPreferredSize().width, pinnedHeight);
            }

            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(super.getMaximumSize().width, pinnedHeight);
            }

            @Override
            public Dimension getMinimumSize()
            {
                return new Dimension(super.getMinimumSize().width, pinnedHeight);
            }

            @Override
            public void setBounds(int x, int y, int width, int height)
            {
                super.setBounds(x, y, width, Math.min(height, pinnedHeight));
            }
        };
        combo.setAlignmentX(Component.CENTER_ALIGNMENT);
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.setHorizontalAlignment(DefaultListCellRenderer.CENTER);
        combo.setRenderer(renderer);
        return combo;
    }

    public static JTextField centeredTextField()
    {
        JTextField field = new JTextField();
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        int height = field.getPreferredSize().height;
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return field;
    }

    public static JPasswordField centeredPasswordField()
    {
        JPasswordField field = new JPasswordField();
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        int height = field.getPreferredSize().height;
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return field;
    }
}
