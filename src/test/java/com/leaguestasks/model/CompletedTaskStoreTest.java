package com.leaguestasks.model;

import com.leaguestasks.ConfigKeys;
import com.google.gson.Gson;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CompletedTaskStoreTest
{
    private static final String ACCOUNT_KEY = ConfigKeys.COMPLETED_TASKS_PREFIX + "test";

    private ConfigManager configManager;
    private CompletedTaskStore store;
    private final Gson gson = new Gson();
    private TaskEntry task;

    @Before
    public void setUp()
    {
        configManager = mock(ConfigManager.class);
        store = new CompletedTaskStore(configManager, gson);
        store.setAccount("test");
        task = gson.fromJson("{\"id\":\"defeat-vardorvis\",\"name\":\"Defeat Vardorvis\"}", TaskEntry.class);
    }

    @Test
    public void markCompletedAppendsAndDeduplicatesIds()
    {
        when(configManager.getConfiguration(ConfigKeys.GROUP, ACCOUNT_KEY)).thenReturn(null);

        store.markCompleted(task);
        store.markCompleted(task);

        Set<String> ids = store.completedTaskIds();
        assertEquals(1, ids.size());
        assertTrue(ids.contains("defeat-vardorvis"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configManager, org.mockito.Mockito.times(1)).setConfiguration(
            eq(ConfigKeys.GROUP), eq(ACCOUNT_KEY), json.capture());
        assertEquals(1, gson.fromJson(json.getValue(), CompletedTaskRecord[].class).length);
    }

    @Test
    public void emptyAndCorruptJsonYieldEmptySet()
    {
        when(configManager.getConfiguration(ConfigKeys.GROUP, ACCOUNT_KEY)).thenReturn("");
        assertTrue(store.completedTaskIds().isEmpty());

        store = new CompletedTaskStore(configManager, gson);
        store.setAccount("test");
        when(configManager.getConfiguration(ConfigKeys.GROUP, ACCOUNT_KEY)).thenReturn("[");
        assertTrue(store.completedTaskIds().isEmpty());
    }
}
