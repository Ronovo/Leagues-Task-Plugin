package com.leaguestasks.ui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * A vertical stack of buttons that fills the sidebar's height.
 *
 * <p>{@link net.runelite.client.ui.PluginPanel} packs to the card's preferred
 * height and pins it to the top, so {@code Box.createVerticalGlue()} would
 * have no slack to absorb and the stack would jam against the top of the
 * panel. Stretching to the viewport gives the glue something to work with.
 *
 * <p>The insets walk is depth-agnostic on purpose: it works no matter how
 * many bordered containers sit between this card and the viewport.
 */
public abstract class MenuCard extends JPanel
{
    protected MenuCard()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension size = super.getPreferredSize();
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport == null)
        {
            return size;
        }

        int available = viewport.getExtentSize().height;
        for (Container parent = getParent(); parent != null && parent != viewport; parent = parent.getParent())
        {
            if (parent instanceof JComponent)
            {
                Insets insets = ((JComponent) parent).getInsets();
                available -= insets.top + insets.bottom;
            }
        }

        if (available > size.height)
        {
            return new Dimension(size.width, available);
        }
        return size;
    }
}
