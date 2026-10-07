package com.leaguestasks;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.leaguestasks.sync.CollectionLogCapture;
import com.leaguestasks.sync.CollectionLogSyncButton;
import com.leaguestasks.sync.TaskAutoDetector;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.ClientToolbar;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
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
    private CollectionLogCapture collectionLogCapture;
    private CollectionLogSyncButton collectionLogSyncButton;
    private TaskAutoDetector taskAutoDetector;
    private ClientToolbar clientToolbar;
    private LeaguesTasksPlugin plugin;

    @Before
    public void setUp()
    {
        collectionLogCapture = mock(CollectionLogCapture.class);
        collectionLogSyncButton = mock(CollectionLogSyncButton.class);
        taskAutoDetector = mock(TaskAutoDetector.class);
        clientToolbar = mock(ClientToolbar.class);

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig(any())).thenReturn(mock(LeaguesTasksConfig.class));

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
                bind(CollectionLogCapture.class).toInstance(collectionLogCapture);
                bind(CollectionLogSyncButton.class).toInstance(collectionLogSyncButton);
                bind(TaskAutoDetector.class).toInstance(taskAutoDetector);
            }
        });

        plugin = injector.getInstance(LeaguesTasksPlugin.class);
    }

    @Test
    public void doubleStartUpRegistersEachSubsystemOnce() throws Exception
    {
        plugin.startUp();
        plugin.startUp();

        verify(collectionLogCapture, times(1)).startUp();
        verify(collectionLogSyncButton, times(1)).startUp();
        verify(taskAutoDetector, times(1)).startUp();
    }

    @Test
    public void reToggleCyclesEachSubsystemEvenly() throws Exception
    {
        plugin.startUp();
        plugin.shutDown();
        plugin.startUp();
        plugin.shutDown();

        verify(collectionLogCapture, times(2)).startUp();
        verify(collectionLogCapture, times(2)).shutDown();
        verify(taskAutoDetector, times(2)).startUp();
        verify(taskAutoDetector, times(2)).shutDown();
        verify(collectionLogSyncButton, times(2)).startUp();
        verify(collectionLogSyncButton, times(2)).shutDown();
        verify(clientToolbar, times(2)).addNavigation(any());
        verify(clientToolbar, times(2)).removeNavigation(any());
    }

    @Test
    public void shutDownBeforeStartUpDoesNotStopUnstartedSubsystems()
    {
        plugin.shutDown();

        verify(collectionLogCapture, times(0)).shutDown();
        verify(collectionLogSyncButton, times(0)).shutDown();
        verify(taskAutoDetector, times(0)).shutDown();
        verify(clientToolbar, times(1)).removeNavigation(any());
    }
}
