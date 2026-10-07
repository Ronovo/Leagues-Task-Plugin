package com.leaguestasks.model;

import com.google.gson.Gson;
import com.leaguestasks.ConfigKeys;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Background log of completed tasks, persisted via ConfigManager as a
 * single JSON array (same storage pattern as SavedTaskStore).
 *
 * <p><b>Scoped per account.</b> Each RS display name gets its own blob
 * under {@link ConfigKeys#COMPLETED_TASKS_PREFIX}. That separation is
 * load-bearing rather than cosmetic: Autocomplete Tasks derives completions
 * from game state, so a shared list would let an alt's levels silently
 * overwrite the main's progress. Call {@link #setAccount(String)} as soon
 * as a display name is known; until then the store reads the last account
 * seen. Account/key plumbing is shared via {@link AccountScopedConfig}.
 */
@Singleton
public class CompletedTaskStore
{
    private static final Type LIST_TYPE = new TypeToken<List<CompletedTaskRecord>>()
    {
    }.getType();

    private final AccountScopedConfig scope;
    private List<CompletedTaskRecord> records;
    private Set<String> ids;

    @Inject
    public CompletedTaskStore(ConfigManager configManager, Gson gson)
    {
        this.scope = new AccountScopedConfig(configManager, gson);
    }

    /**
     * Points the store at one account's list, dropping whatever was cached
     * for the previous one. Safe to call on every login: a repeat call for
     * the same account is a no-op, so it will not churn the cache.
     *
     * @return true if the active account changed, meaning any UI showing
     * completion state needs a repaint.
     */
    public synchronized boolean setAccount(String displayName)
    {
        boolean changed = scope.setAccount(displayName);
        if (changed)
        {
            records = null;
            ids = null;
        }
        return changed;
    }

    public synchronized boolean markCompleted(TaskEntry task)
    {
        return markCompleted(task, CompletedTaskRecord.Source.MANUAL);
    }

    /**
     * As {@link #markCompleted(TaskEntry)}, recording where the completion
     * came from. A task already marked by hand stays marked by hand -- the
     * dedup guard means a later auto-detection cannot downgrade it.
     */
    public synchronized boolean markCompleted(TaskEntry task, CompletedTaskRecord.Source source)
    {
        ensureLoaded();
        if (!ids.add(task.getId()))
        {
            return false;
        }
        records.add(new CompletedTaskRecord(task.getId(), Instant.now().toString(), source));
        persist();
        return true;
    }

    /**
     * Distinct task ids completed -- used both to mark individual tasks as
     * done and to count completions within a filtered set. Every id here
     * has exactly one record in {@link #allRecords()}.
     */
    public synchronized Set<String> completedTaskIds()
    {
        ensureLoaded();
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Every completion record, one per task id, in the order they were
     * recorded -- the full snapshot a progress sync sends.
     */
    public synchronized List<CompletedTaskRecord> allRecords()
    {
        ensureLoaded();
        return Collections.unmodifiableList(records);
    }

    private synchronized void ensureLoaded()
    {
        if (records != null)
        {
            return;
        }

        String key = scope.storageKey(ConfigKeys.COMPLETED_TASKS_PREFIX);
        List<CompletedTaskRecord> loaded = scope.read(key, LIST_TYPE, ArrayList::new);

        // Storage saved before the markCompleted() dedup guard existed may
        // have multiple records for the same task id -- drop everything
        // past the first (earliest) one so a stale duplicate is never
        // resurfaced, in memory or in what a sync sends.
        ids = new HashSet<>();
        records = new ArrayList<>(loaded.size());
        for (CompletedTaskRecord record : loaded)
        {
            if (record != null && record.getTaskId() != null && ids.add(record.getTaskId()))
            {
                records.add(record);
            }
        }

        if (records.size() != loaded.size())
        {
            persist();
        }
    }

    private synchronized void persist()
    {
        String key = scope.storageKey(ConfigKeys.COMPLETED_TASKS_PREFIX);
        if (key == null)
        {
            return;
        }

        scope.write(key, records);
    }
}
