package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.leaguestasks.ConfigKeys;
import com.leaguestasks.InMemoryConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectionLogCaptureTest
{
    private static final int SEARCH_RESULT_SCRIPT = 4100;
    private static final int RUN_SEARCH_SCRIPT = 2240;
    private static final int UNSIRED_DUMMY_ID = 25624;
    private static final int OTHER_ITEM_ID = 12921;

    private Map<String, String> config;
    private ConfigManager configManager;
    private Client client;
    private CollectionLogCatalog catalog;
    private CollectionLogCapture capture;
    private final Gson gson = new Gson();

    @Before
    public void setUp()
    {
        config = new HashMap<>();
        configManager = InMemoryConfig.backedBy(config);

        client = mock(Client.class);
        catalog = mock(CollectionLogCatalog.class);
        capture = new CollectionLogCapture(client, configManager, gson, mock(EventBus.class), catalog);
        capture.setAccount("test");
    }

    @Test
    public void captureRunsSearchAndBuildsFromEveryReportedId()
    {
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(mock(Widget.class));
        when(client.getTickCount()).thenReturn(10);

        CollectionLogSnapshot built = snapshot();
        when(catalog.build(any())).thenReturn(built);

        AtomicReference<CollectionLogSnapshot> result = new AtomicReference<>();
        capture.capture(result::set);

        verify(client).runScript(RUN_SEARCH_SCRIPT);
        verify(client).menuAction(eq(-1), eq(InterfaceID.Collection.SEARCH_TOGGLE),
            eq(MenuAction.CC_OP), eq(1), eq(-1), eq("Search"), eq(null));

        capture.onScriptPreFired(scriptResult(UNSIRED_DUMMY_ID));
        capture.onScriptPreFired(scriptResult(OTHER_ITEM_ID));
        when(client.getTickCount()).thenReturn(12);
        capture.onGameTick(new GameTick());

        verify(catalog).build(eq(Set.of(UNSIRED_DUMMY_ID, OTHER_ITEM_ID)));
        assertSame(built, result.get());
        assertEquals(gson.toJson(built), config.get(ConfigKeys.COLLECTION_LOG_PREFIX + "test"));
    }

    @Test
    public void searchTimeoutUsesCacheInsteadOfAnEmptyDump()
    {
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(mock(Widget.class));
        when(client.getTickCount()).thenReturn(1);

        CollectionLogSnapshot cached = snapshot();
        config.put(ConfigKeys.COLLECTION_LOG_PREFIX + "test", gson.toJson(cached));

        AtomicReference<CollectionLogSnapshot> result = new AtomicReference<>();
        capture.capture(result::set);
        for (int i = 0; i < 10; i++)
        {
            capture.onGameTick(new GameTick());
        }

        verify(catalog, never()).build(any());
        assertEquals(cached.getItemsObtained(), result.get().getItemsObtained());
    }

    @Test
    public void closedInterfaceUsesCacheWithoutRunningSearch()
    {
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(null);
        CollectionLogSnapshot cached = snapshot();
        config.put(ConfigKeys.COLLECTION_LOG_PREFIX + "test", gson.toJson(cached));

        AtomicReference<CollectionLogSnapshot> result = new AtomicReference<>();
        capture.capture(result::set);

        verify(client, never()).runScript(RUN_SEARCH_SCRIPT);
        assertEquals(cached.getItemsObtained(), result.get().getItemsObtained());
    }

    @Test
    public void catalogFailureFallsBackToTheCachedDump()
    {
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(mock(Widget.class));
        when(client.getTickCount()).thenReturn(10);
        when(catalog.build(any())).thenThrow(new NullPointerException("unexpected cache shape"));

        CollectionLogSnapshot cached = snapshot();
        config.put(ConfigKeys.COLLECTION_LOG_PREFIX + "test", gson.toJson(cached));

        AtomicReference<CollectionLogSnapshot> result = new AtomicReference<>();
        capture.capture(result::set);
        capture.onScriptPreFired(scriptResult(OTHER_ITEM_ID));
        when(client.getTickCount()).thenReturn(12);
        capture.onGameTick(new GameTick());

        assertEquals(cached.getItemsObtained(), result.get().getItemsObtained());
    }

    @Test
    public void shutDownReleasesAPendingCapture()
    {
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(mock(Widget.class));

        AtomicReference<CollectionLogSnapshot> result = new AtomicReference<>();
        capture.capture(result::set);
        capture.shutDown();

        verify(catalog, never()).build(any());
        assertNull(result.get());
    }

    private static ScriptPreFired scriptResult(int itemId)
    {
        ScriptEvent scriptEvent = mock(ScriptEvent.class);
        when(scriptEvent.getArguments()).thenReturn(new Object[] {SEARCH_RESULT_SCRIPT, itemId});
        ScriptPreFired event = mock(ScriptPreFired.class);
        when(event.getScriptId()).thenReturn(SEARCH_RESULT_SCRIPT);
        when(event.getScriptEvent()).thenReturn(scriptEvent);
        return event;
    }

    private static CollectionLogSnapshot snapshot()
    {
        return new CollectionLogSnapshot(1, 2, Collections.singletonList(
            new CollectionLogGroup("Bosses",
                Collections.singletonList(new CollectionLogCategory("zulrah", "Zulrah",
                    Collections.singletonList(new CollectionLogItem(12921, "Tanzanite fang", 1)))))));
    }
}
