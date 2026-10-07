package com.leaguestasks.ui;

import com.leaguestasks.ConfigKeys;
import com.leaguestasks.InMemoryConfig;
import com.leaguestasks.LeaguesTasksConfig;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SyncSettingsCardTest
{
    private final Map<String, String> values = new HashMap<>();
    private SyncSettingsCard card;
    private JTextField serverUrlField;
    private JPasswordField apiTokenField;

    @Before
    public void setUp()
    {
        LeaguesTasksConfig config = mock(LeaguesTasksConfig.class);
        when(config.serverUrl()).thenReturn("https://tracker.example");
        when(config.apiToken()).thenReturn("saved-token");
        card = new SyncSettingsCard(InMemoryConfig.backedBy(values), config, mock(Navigator.class));

        for (Component c : card.getComponents())
        {
            if (c instanceof JPasswordField)
            {
                apiTokenField = (JPasswordField) c;
            }
            else if (c instanceof JTextField)
            {
                serverUrlField = (JTextField) c;
            }
        }
    }

    @Test
    public void openingTheCardShowsTheSavedValues()
    {
        card.onOpened();

        assertEquals("https://tracker.example", serverUrlField.getText());
        assertEquals("saved-token", new String(apiTokenField.getPassword()));
    }

    @Test
    public void leavingAFieldSavesBothValuesTrimmed()
    {
        serverUrlField.setText("  http://localhost:4123  ");
        apiTokenField.setText(" new-token ");

        loseFocus(apiTokenField);

        assertEquals("http://localhost:4123", values.get(ConfigKeys.SERVER_URL));
        assertEquals("new-token", values.get(ConfigKeys.API_TOKEN));
    }

    private static void loseFocus(JTextField field)
    {
        FocusEvent event = new FocusEvent(field, FocusEvent.FOCUS_LOST);
        for (FocusListener listener : field.getFocusListeners())
        {
            listener.focusLost(event);
        }
    }
}
