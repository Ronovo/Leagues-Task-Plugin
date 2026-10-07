package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.leaguestasks.ConfigKeys;
import com.leaguestasks.InMemoryConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class CollectionLogCaptureAccountTest
{
    private Map<String, String> config;
    private ConfigManager configManager;
    private CollectionLogCapture capture;
    private final Gson gson = new Gson();

    @Before
    public void setUp()
    {
        config = new HashMap<>();
        configManager = InMemoryConfig.backedBy(config);

        capture = new CollectionLogCapture(
            mock(net.runelite.api.Client.class),
            configManager,
            gson,
            mock(net.runelite.client.eventbus.EventBus.class),
            mock(CollectionLogCatalog.class));
    }

    private static CollectionLogSnapshot snapshot()
    {
        return new CollectionLogSnapshot(1, 2, Collections.singletonList(
            new CollectionLogGroup("Bosses",
                Collections.singletonList(new CollectionLogCategory("zulrah", "Zulrah",
                    Collections.singletonList(new CollectionLogItem(12921, "Tanzanite fang", 1)))))));
    }

    @Test
    public void accountsKeepSeparateCachedSnapshots()
    {
        capture.setAccount("Zezima");
        config.put(ConfigKeys.COLLECTION_LOG_PREFIX + "zezima", gson.toJson(snapshot()));

        capture.setAccount("Some Alt");
        assertNull(capture.cachedSnapshot());

        config.put(ConfigKeys.COLLECTION_LOG_PREFIX + "some_alt",
            gson.toJson(new CollectionLogSnapshot(0, 1, Collections.emptyList())));
        assertNotNull(capture.cachedSnapshot());
        assertEquals(0, capture.cachedSnapshot().getItemsObtained());
    }
}
