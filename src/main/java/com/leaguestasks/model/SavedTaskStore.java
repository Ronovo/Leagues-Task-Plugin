package com.leaguestasks.model;

import com.google.gson.Gson;
import com.leaguestasks.ConfigKeys;
import com.google.gson.reflect.TypeToken;
import com.leaguestasks.ui.TextUtil;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Persists at most one saved task per difficulty tier (a lightweight "my
 * list"), stored as a single JSON blob via RuneLite's ConfigManager. This is
 * internal app state rather than a user-facing setting, so it's stored
 * directly through ConfigManager's generic get/setConfiguration rather than
 * a {@code @ConfigItem}-backed Config interface.
 *
 * <p>Scoped per account under {@link ConfigKeys#SAVED_TASKS_PREFIX}, same
 * as completions (account/key plumbing is shared via {@link
 * AccountScopedConfig}). A blob written before per-account scoping existed is
 * still read back from the unscoped {@link ConfigKeys#SAVED_TASKS} key. A
 * completed task never counts as saved: {@link #getSaved} hides one that has
 * since been marked complete, and {@link #pruneCompleted()} drops it from
 * storage. Call {@link #setAccount(String)} on login.
 */
@Singleton
public class SavedTaskStore
{
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>()
    {
    }.getType();

    private final AccountScopedConfig scope;
    private final TaskRepository repository;
    private final CompletedTaskStore completedTaskStore;

    @Inject
    public SavedTaskStore(ConfigManager configManager, TaskRepository repository, Gson gson,
        CompletedTaskStore completedTaskStore)
    {
        this.scope = new AccountScopedConfig(configManager, gson);
        this.repository = repository;
        this.completedTaskStore = completedTaskStore;
    }

    public boolean setAccount(String displayName)
    {
        return scope.setAccount(displayName);
    }

    public boolean hasSaved(String difficulty)
    {
        return getSaved(difficulty).isPresent();
    }

    public Optional<TaskEntry> getSaved(String difficulty)
    {
        String taskId = load().get(key(difficulty));
        if (taskId == null || completedTaskStore.completedTaskIds().contains(taskId))
        {
            return Optional.empty();
        }
        return repository.findById(taskId);
    }

    /**
     * Drops any saved slot whose task has since been completed. Cheap to call
     * whenever the list is shown; only writes when something actually changed.
     */
    public void pruneCompleted()
    {
        Map<String, String> saved = load();
        Set<String> completed = completedTaskStore.completedTaskIds();
        if (saved.values().removeIf(completed::contains))
        {
            persist(saved);
        }
    }

    public void save(TaskEntry task)
    {
        Map<String, String> saved = load();
        saved.put(key(task.getDifficulty()), task.getId());
        persist(saved);
    }

    public void clear(String difficulty)
    {
        Map<String, String> saved = load();
        if (saved.remove(key(difficulty)) != null)
        {
            persist(saved);
        }
    }

    private static String key(String difficulty)
    {
        return difficulty == null ? "" : difficulty.toLowerCase(Locale.ENGLISH);
    }

    private Map<String, String> load()
    {
        String key = scope.storageKey(ConfigKeys.SAVED_TASKS_PREFIX);
        String json = scope.readRaw(key);
        if (TextUtil.isBlank(json))
        {
            // Blob written before per-account scoping.
            json = scope.readRaw(ConfigKeys.SAVED_TASKS);
        }
        return scope.parse(key != null ? key : ConfigKeys.SAVED_TASKS, json, MAP_TYPE, HashMap::new);
    }

    private void persist(Map<String, String> saved)
    {
        String key = scope.storageKey(ConfigKeys.SAVED_TASKS_PREFIX);
        if (key == null)
        {
            key = ConfigKeys.SAVED_TASKS;
        }
        scope.write(key, saved);
    }
}
