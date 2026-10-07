package com.leaguestasks.model;

import com.leaguestasks.ConfigKeys;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SavedTaskStoreTest
{
    private ConfigManager configManager;
    private TaskRepository repository;
    private CompletedTaskStore completedTaskStore;
    private SavedTaskStore store;
    private final Gson gson = new Gson();
    private TaskEntry easyTask;

    @Before
    public void setUp()
    {
        configManager = mock(ConfigManager.class);
        repository = mock(TaskRepository.class);
        completedTaskStore = mock(CompletedTaskStore.class);
        when(completedTaskStore.completedTaskIds()).thenReturn(Collections.emptySet());
        store = new SavedTaskStore(configManager, repository, gson, completedTaskStore);
        store.setAccount("test");
        easyTask = sampleTask();
        when(repository.findById("1-wintertodt-kill")).thenReturn(Optional.of(easyTask));
    }

    @Test
    public void saveAndReadRoundTrip()
    {
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn(null);

        store.save(easyTask);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configManager).setConfiguration(
            org.mockito.ArgumentMatchers.eq(ConfigKeys.GROUP),
            org.mockito.ArgumentMatchers.eq(ConfigKeys.SAVED_TASKS_PREFIX + "test"),
            json.capture());

        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn(json.getValue());
        assertTrue(store.hasSaved("easy"));
        assertEquals("1-wintertodt-kill", store.getSaved("Easy").get().getId());
    }

    @Test
    public void hasSavedAgreesWithGetSavedWhenIdIsMissing()
    {
        Map<String, String> saved = new HashMap<>();
        saved.put("easy", "missing-task");
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test"))
            .thenReturn(gson.toJson(saved));
        when(repository.findById("missing-task")).thenReturn(Optional.empty());

        assertFalse(store.hasSaved("easy"));
        assertFalse(store.getSaved("easy").isPresent());
    }

    @Test
    public void clearRemovesSavedSlot()
    {
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn(null);
        store.save(easyTask);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configManager).setConfiguration(
            org.mockito.ArgumentMatchers.eq(ConfigKeys.GROUP),
            org.mockito.ArgumentMatchers.eq(ConfigKeys.SAVED_TASKS_PREFIX + "test"),
            json.capture());

        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn(json.getValue());
        assertTrue(store.hasSaved("easy"));

        store.clear("easy");

        verify(configManager, org.mockito.Mockito.times(2)).setConfiguration(
            org.mockito.ArgumentMatchers.eq(ConfigKeys.GROUP),
            org.mockito.ArgumentMatchers.eq(ConfigKeys.SAVED_TASKS_PREFIX + "test"),
            json.capture());
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn(json.getValue());
        assertFalse(store.hasSaved("easy"));
    }

    @Test
    public void getSavedHidesATaskThatHasSinceBeenCompleted()
    {
        Map<String, String> saved = new HashMap<>();
        saved.put("easy", "1-wintertodt-kill");
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test"))
            .thenReturn(gson.toJson(saved));

        assertTrue(store.hasSaved("easy"));

        when(completedTaskStore.completedTaskIds())
            .thenReturn(new HashSet<>(Collections.singleton("1-wintertodt-kill")));

        assertFalse(store.hasSaved("easy"));
        assertFalse(store.getSaved("easy").isPresent());
    }

    @Test
    public void pruneCompletedRemovesCompletedSlotsFromStorage()
    {
        Map<String, String> saved = new HashMap<>();
        saved.put("easy", "1-wintertodt-kill");
        saved.put("hard", "some-other-task");
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test"))
            .thenReturn(gson.toJson(saved));
        when(completedTaskStore.completedTaskIds())
            .thenReturn(new HashSet<>(Collections.singleton("1-wintertodt-kill")));

        store.pruneCompleted();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configManager).setConfiguration(
            org.mockito.ArgumentMatchers.eq(ConfigKeys.GROUP),
            org.mockito.ArgumentMatchers.eq(ConfigKeys.SAVED_TASKS_PREFIX + "test"),
            json.capture());
        assertFalse(json.getValue().contains("1-wintertodt-kill"));
        assertTrue(json.getValue().contains("some-other-task"));
    }

    @Test
    public void pruneCompletedDoesNotWriteWhenNothingCompleted()
    {
        Map<String, String> saved = new HashMap<>();
        saved.put("easy", "1-wintertodt-kill");
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test"))
            .thenReturn(gson.toJson(saved));

        store.pruneCompleted();

        verify(configManager, org.mockito.Mockito.never()).setConfiguration(
            org.mockito.ArgumentMatchers.eq(ConfigKeys.GROUP),
            org.mockito.ArgumentMatchers.eq(ConfigKeys.SAVED_TASKS_PREFIX + "test"),
            org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void emptyAndCorruptJsonYieldEmptyStore()
    {
        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn("");
        assertFalse(store.hasSaved("easy"));

        when(configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.SAVED_TASKS_PREFIX + "test")).thenReturn("{not json");
        assertFalse(store.hasSaved("hard"));
    }

    private TaskEntry sampleTask()
    {
        return gson.fromJson(
            "{\"id\":\"1-wintertodt-kill\",\"name\":\"1 Wintertodt Kill\",\"difficulty\":\"easy\"}",
            TaskEntry.class);
    }
}
