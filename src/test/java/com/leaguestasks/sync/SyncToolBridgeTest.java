package com.leaguestasks.sync;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncToolBridgeTest
{
    @PluginDescriptor(name = "Ronovo's Sync Tool")
    private static class FakeSyncTool extends Plugin
    {
    }

    @PluginDescriptor(name = "Some Other Plugin")
    private static class OtherPlugin extends Plugin
    {
    }

    private PluginManager pluginManager;
    private EventBus eventBus;
    private SyncToolBridge bridge;

    @Before
    public void setUp()
    {
        pluginManager = mock(PluginManager.class);
        eventBus = mock(EventBus.class);
        bridge = new SyncToolBridge(pluginManager, eventBus);
    }

    @Test
    public void activeWhenTheSyncToolIsInstalledAndOn()
    {
        Plugin syncTool = new FakeSyncTool();
        when(pluginManager.getPlugins()).thenReturn(Arrays.asList(new OtherPlugin(), syncTool));
        when(pluginManager.isPluginActive(syncTool)).thenReturn(true);

        assertTrue(bridge.isSyncToolActive());
    }

    @Test
    public void inactiveWhenTheSyncToolIsInstalledButOff()
    {
        when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(new FakeSyncTool()));

        assertFalse(bridge.isSyncToolActive());
    }

    @Test
    public void inactiveWhenTheSyncToolIsNotInstalled()
    {
        Plugin other = new OtherPlugin();
        when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(other));
        when(pluginManager.isPluginActive(other)).thenReturn(true);

        assertFalse(bridge.isSyncToolActive());
    }

    @Test
    public void taskSyncRequestUsesTheSyncToolsMessage()
    {
        bridge.requestTaskSync();

        ArgumentCaptor<Object> posted = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).post(posted.capture());
        PluginMessage message = (PluginMessage) posted.getValue();
        assertEquals("ronovossync", message.getNamespace());
        assertEquals("syncTasks", message.getName());
    }
}
