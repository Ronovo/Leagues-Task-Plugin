package com.leaguestasks.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JPanel;

/**
 * A {@link CardLayout} panel that sizes to the card actually on screen.
 *
 * <p>{@code CardLayout.preferredLayoutSize()} reports the largest card, so a
 * plain one inside the sidebar's {@link javax.swing.JScrollPane} makes every
 * card scroll as soon as the tallest one does.
 */
public class CardPanel extends JPanel
{
    private final CardLayout cardLayout = new CardLayout();

    public CardPanel()
    {
        setLayout(cardLayout);
        SwingComponents.stylePanel(this);
    }

    public void show(String cardName)
    {
        cardLayout.show(this, cardName);
    }

    @Override
    public Dimension getPreferredSize()
    {
        for (Component c : getComponents())
        {
            if (c.isVisible())
            {
                return c.getPreferredSize();
            }
        }
        return super.getPreferredSize();
    }
}
