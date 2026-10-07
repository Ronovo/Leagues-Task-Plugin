package com.leaguestasks.model;

import com.google.gson.Gson;
import com.leaguestasks.InMemoryConfig;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The account-scoping half of the store: which blob a given character
 * reads and writes.
 */
public class CompletedTaskStoreAccountTest
{
    private Map<String, String> config;
    private ConfigManager configManager;
    private final Gson gson = new Gson();
    private TaskEntry task;

    @Before
    public void setUp()
    {
        config = new HashMap<>();
        configManager = InMemoryConfig.backedBy(config);

        task = gson.fromJson("{\"id\":\"defeat-vardorvis\"}", TaskEntry.class);
    }

    private CompletedTaskStore storeFor(String account)
    {
        CompletedTaskStore store = new CompletedTaskStore(configManager, gson);
        if (account != null)
        {
            store.setAccount(account);
        }
        return store;
    }

    @Test
    public void accountsKeepSeparateLists()
    {
        CompletedTaskStore main = storeFor("Zezima");
        main.markCompleted(task);

        CompletedTaskStore alt = storeFor("Some Alt");
        assertFalse(alt.completedTaskIds().contains("defeat-vardorvis"));

        // Writing on the alt must leave the main's list untouched -- the
        // whole reason scoping exists, since Autocomplete Tasks writes
        // completions derived from the logged-in account's game state.
        alt.markCompleted(gson.fromJson("{\"id\":\"alt-only\"}", TaskEntry.class));
        assertTrue(storeFor("Zezima").completedTaskIds().contains("defeat-vardorvis"));
        assertFalse(storeFor("Zezima").completedTaskIds().contains("alt-only"));
    }

    @Test
    public void displayNameCaseAndNbspNormalizeToOneAccount()
    {
        storeFor("Some Alt").markCompleted(task);

        // The game hands display names back with a non-breaking space
        // rather than a plain one, and case varies between sources --
        // every spelling must land on the same stored list.
        assertTrue(storeFor("some\u00A0alt").completedTaskIds().contains("defeat-vardorvis"));
        assertTrue(storeFor("SOME\u00A0ALT").completedTaskIds().contains("defeat-vardorvis"));
        assertTrue(storeFor("some alt").completedTaskIds().contains("defeat-vardorvis"));
    }

    @Test
    public void loggedOutStoreReadsTheLastAccountSeen()
    {
        storeFor("Zezima").markCompleted(task);

        // No setAccount() call: a panel opened before login still shows the
        // list belonging to whoever played last.
        assertTrue(storeFor(null).completedTaskIds().contains("defeat-vardorvis"));
    }

    @Test
    public void switchingAccountReportsTheChangeOnlyOnce()
    {
        CompletedTaskStore store = new CompletedTaskStore(configManager, gson);
        assertTrue(store.setAccount("Zezima"));
        assertFalse("re-login as the same character must not churn the cache",
            store.setAccount("Zezima"));
        assertTrue(store.setAccount("Some Alt"));
    }
}
