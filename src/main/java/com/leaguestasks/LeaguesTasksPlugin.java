package com.leaguestasks;

import com.google.inject.Provides;
import com.leaguestasks.sync.CollectionLogCapture;
import com.leaguestasks.sync.CollectionLogSyncButton;
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
 * RuneLite plugin entry point: browse and randomise Leagues tasks, and sync
 * progress to a personal tracker.
 */
@PluginDescriptor(
    name = "Leagues Tasks",
    description = "Browse and randomly pick a task from a unified list of every past OSRS Leagues event, "
        + "filterable by region, difficulty, and activity type, with progress sync and autocomplete.",
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
    private CollectionLogCapture collectionLogCapture;

    @Inject
    private CollectionLogSyncButton collectionLogSyncButton;

    @Inject
    private TaskAutoDetector taskAutoDetector;

    private NavigationButton navButton;

    /**
     * {@link CollectionLogCapture}/{@link CollectionLogSyncButton}/
     * {@link TaskAutoDetector} subscribe to game events themselves rather
     * than relying on a Plugin's {@code @Subscribe} auto-registration, so
     * they need pairing with shutDown() -- and must not double-register on
     * a plugin re-toggle.
     */
    private boolean started;

    @Provides
    LeaguesTasksConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(LeaguesTasksConfig.class);
    }

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
            collectionLogCapture.startUp();
            collectionLogSyncButton.startUp();
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
            collectionLogSyncButton.shutDown();
            collectionLogCapture.shutDown();
        }

        clientToolbar.removeNavigation(navButton);
    }
}
