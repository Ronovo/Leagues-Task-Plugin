package com.leaguestasks;

import com.leaguestasks.sync.TaskAutoDetector;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * RuneLite plugin entry point: browse and randomise Leagues tasks, and
 * autocomplete what the client can verify.
 */
@PluginDescriptor(
    name = "Leagues Tasks",
    description = "Browse and randomly pick a task from a unified list of every past OSRS Leagues event, "
        + "filterable by region, difficulty, and activity type, with autocomplete.",
    tags = {"leagues", "tasks", "randomizer", "ironman"}
)
public class LeaguesTasksPlugin extends Plugin
{
    private static final int NAV_PRIORITY = 6;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private LeaguesTasksPanel panel;

    @Inject
    private ConfigManager configManager;

    @Inject
    private TaskAutoDetector taskAutoDetector;

    private NavigationButton navButton;

    /**
     * {@link TaskAutoDetector} subscribes to game events itself rather than
     * relying on a Plugin's {@code @Subscribe} auto-registration, so it needs
     * pairing with shutDown() -- and must not double-register on a plugin
     * re-toggle.
     */
    private boolean started;

    @Override
    protected void startUp() throws Exception
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            panel.init();
        }
        else
        {
            SwingUtilities.invokeAndWait(panel::init);
        }

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Leagues Tasks")
            .icon(icon)
            .priority(NAV_PRIORITY)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        if (!started)
        {
            started = true;
            removeMovedSyncKeys();
            taskAutoDetector.setOnAccountChanged(panel::refreshCompletionState);
            taskAutoDetector.startUp();
        }
    }

    @Override
    protected void shutDown()
    {
        if (started)
        {
            started = false;
            taskAutoDetector.shutDown();
        }

        clientToolbar.removeNavigation(navButton);
    }

    // TODO: remove once existing installs have run this version. Syncing moved
    // to Ronovo's Sync Tool, which starts with its own clean settings, so these
    // keys are no longer read by anything.
    private void removeMovedSyncKeys()
    {
        configManager.unsetConfiguration(ConfigKeys.GROUP, "serverUrl");
        configManager.unsetConfiguration(ConfigKeys.GROUP, "apiToken");
        String groupPrefix = ConfigKeys.GROUP + ".";
        for (String key : configManager.getConfigurationKeys(groupPrefix + "leagues.collectionLog"))
        {
            configManager.unsetConfiguration(ConfigKeys.GROUP, key.substring(groupPrefix.length()));
        }
    }
}
