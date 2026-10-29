package com.leaguestasks.ui;

import com.leaguestasks.sync.SyncToolBridge;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StartCardTest
{
    private Navigator navigator;
    private SyncToolBridge bridge;

    @Before
    public void setUp()
    {
        navigator = mock(Navigator.class);
        bridge = mock(SyncToolBridge.class);
    }

    @Test
    public void withoutTheSyncToolTheButtonOpensTheTrackerCard()
    {
        StartCard card = new StartCard(navigator, bridge);
        JButton button = findButton(card, "Install Ronovo's Sync Tool");
        assertNotNull(button);

        button.doClick();

        verify(navigator).showCard(Navigator.CARD_TRACKER);
        verify(bridge, never()).requestTaskSync();
    }

    @Test
    public void withTheSyncToolTheButtonRequestsATaskSync()
    {
        when(bridge.isSyncToolActive()).thenReturn(true);
        StartCard card = new StartCard(navigator, bridge);
        JButton button = findButton(card, "Sync Progress");
        assertNotNull(button);

        button.doClick();

        verify(bridge).requestTaskSync();
        verify(navigator, never()).showCard(Navigator.CARD_TRACKER);
    }

    @Test
    public void refreshFollowsTheSyncToolBeingTurnedOn()
    {
        StartCard card = new StartCard(navigator, bridge);
        JButton button = findButton(card, "Install Ronovo's Sync Tool");

        when(bridge.isSyncToolActive()).thenReturn(true);
        card.refreshSyncButton();

        assertEquals("Sync Progress", button.getText());
    }

    private static JButton findButton(Container container, String text)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JButton && text.equals(((JButton) child).getText()))
            {
                return (JButton) child;
            }
        }
        return null;
    }
}
