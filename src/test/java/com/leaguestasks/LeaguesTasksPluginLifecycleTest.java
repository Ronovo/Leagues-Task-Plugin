package com.leaguestasks;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.leaguestasks.sync.TaskAutoDetector;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.ClientToolbar;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The {@code started} guard exists so a plugin re-toggle doesn't
 * double-register the event-driven subsystems (they subscribe to the event
 * bus themselves rather than through {@code @Subscribe} auto-registration).
 */
public class LeaguesTasksPluginLifecycleTest
{
    private TaskAutoDetector taskAutoDetector;
    private ClientToolbar clientToolbar;
    private ConfigManager configManager;
    private LeaguesTasksPlugin plugin;

    @Before
    public void setUp()
    {
        taskAutoDetector = mock(TaskAutoDetector.class);
        clientToolbar = mock(ClientToolbar.class);

        configManager = mock(ConfigManager.class);

        Injector injector = Guice.createInjector(new LeaguesTasksPlugin(), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ClientToolbar.class).toInstance(clientToolbar);
                bind(ConfigManager.class).toInstance(configManager);
                bind(EventBus.class).toInstance(mock(EventBus.class));
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                bind(Gson.class).toInstance(new Gson());
                bind(LeaguesTasksPanel.class).toInstance(mock(LeaguesTasksPanel.class));
                bind(TaskAutoDetector.class).toInstance(taskAutoDetector);
            }
        });

        plugin = injector.getInstance(LeaguesTasksPlugin.class);
    }

    @Test
    public void startUpRemovesKeysThatMovedToTheSyncTool() throws Exception
    {
        when(configManager.getConfigurationKeys("leaguestasks.leagues.collectionLog")).thenReturn(Arrays.asList(
            "leaguestasks.leagues.collectionLog.zezima", "leaguestasks.leagues.collectionLog.some_alt"));

        plugin.startUp();

        verify(configManager).unsetConfiguration("leaguestasks", "serverUrl");
        verify(configManager).unsetConfiguration("leaguestasks", "apiToken");
        verify(configManager).unsetConfiguration("leaguestasks", "leagues.collectionLog.zezima");
        verify(configManager).unsetConfiguration("leaguestasks", "leagues.collectionLog.some_alt");
        verify(configManager, times(0)).unsetConfiguration(any(), startsWith("leagues.completedTasks"));
    }

    @Test
    public void doubleStartUpRegistersEachSubsystemOnce() throws Exception
    {
        plugin.startUp();
        plugin.startUp();

        verify(taskAutoDetector, times(1)).startUp();
    }

    @Test
    public void reToggleCyclesEachSubsystemEvenly() throws Exception
    {
        plugin.startUp();
        plugin.shutDown();
        plugin.startUp();
        plugin.shutDown();

        verify(taskAutoDetector, times(2)).startUp();
        verify(taskAutoDetector, times(2)).shutDown();
        verify(clientToolbar, times(2)).addNavigation(any());
        verify(clientToolbar, times(2)).removeNavigation(any());
    }

    @Test
    public void shutDownBeforeStartUpDoesNotStopUnstartedSubsystems()
    {
        plugin.shutDown();

        verify(taskAutoDetector, times(0)).shutDown();
        verify(clientToolbar, times(1)).removeNavigation(any());
    }
}
