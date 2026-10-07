package com.leaguestasks.ui;

import com.leaguestasks.ConfigKeys;
import com.leaguestasks.LeaguesTasksConfig;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import net.runelite.client.config.ConfigManager;

/**
 * The Server URL / API Token fields, split out of {@link SyncCard} so that
 * card can stay focused on the sync actions themselves. These are the same
 * {@link LeaguesTasksConfig} keys RuneLite's own plugin settings panel
 * shows, so editing either place updates the other.
 */
public class SyncSettingsCard extends JPanel
{
    private final ConfigManager configManager;
    private final LeaguesTasksConfig config;

    private final JTextField serverUrlField;
    private final JPasswordField apiTokenField;

    public SyncSettingsCard(ConfigManager configManager, LeaguesTasksConfig config, Navigator navigator)
    {
        this.configManager = configManager;
        this.config = config;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        add(Box.createVerticalGlue());

        JLabel heading = SwingComponents.centeredLabel("Sync Settings");
        heading.setForeground(SwingComponents.ACCENT_COLOR);
        add(heading);
        add(Box.createVerticalStrut(6));

        JLabel subheading = SwingComponents.wrappingCenteredLabel();
        SwingComponents.setCenteredHtml(subheading,
            "Where the Sync to Website card sends your data.");
        add(subheading);
        add(Box.createVerticalStrut(14));

        add(SwingComponents.centeredLabel("Server URL"));
        add(Box.createVerticalStrut(4));
        serverUrlField = SwingComponents.centeredTextField();
        serverUrlField.setToolTipText("http://localhost:4123");
        addFocusSave(serverUrlField);
        add(serverUrlField);
        add(Box.createVerticalStrut(10));

        add(SwingComponents.centeredLabel("API Token"));
        add(Box.createVerticalStrut(4));
        apiTokenField = SwingComponents.centeredPasswordField();
        addFocusSave(apiTokenField);
        add(apiTokenField);
        add(Box.createVerticalStrut(14));

        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.showSync());
        add(backButton);
        add(Box.createVerticalGlue());
    }

    public void onOpened()
    {
        serverUrlField.setText(config.serverUrl());
        apiTokenField.setText(config.apiToken());
    }

    private void addFocusSave(JTextField field)
    {
        field.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                saveConfig();
            }
        });
    }

    private void saveConfig()
    {
        configManager.setConfiguration(
            ConfigKeys.GROUP,
            ConfigKeys.SERVER_URL,
            serverUrlField.getText().trim());
        configManager.setConfiguration(
            ConfigKeys.GROUP,
            ConfigKeys.API_TOKEN,
            new String(apiTokenField.getPassword()).trim());
    }
}
