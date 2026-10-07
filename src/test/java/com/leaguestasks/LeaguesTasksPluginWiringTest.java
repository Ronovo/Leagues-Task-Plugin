package com.leaguestasks;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.ClientToolbar;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The object graph resolves end to end.
 *
 * <p>A missing or miscycled Guice binding compiles cleanly and only
 * fails when RuneLite starts the plugin, so this walks the same graph the
 * client would: the plugin and the panel it hosts.
 */
public class LeaguesTasksPluginWiringTest
{
    @Test
    public void objectGraphResolves()
    {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfig(any())).thenReturn(mock(LeaguesTasksConfig.class));

        Injector injector = Guice.createInjector(new LeaguesTasksPlugin(), new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ClientToolbar.class).toInstance(mock(ClientToolbar.class));
                bind(ConfigManager.class).toInstance(configManager);
                bind(EventBus.class).toInstance(mock(EventBus.class));
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                bind(Gson.class).toInstance(new Gson());
            }
        });

        assertNotNull(injector.getInstance(LeaguesTasksPanel.class));
    }
}
