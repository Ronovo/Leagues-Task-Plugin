package com.leaguestasks.sync;

import com.leaguestasks.model.CompletedTaskRecord;
import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.RuleType;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Works out which tasks the client can prove are done -- quest completion,
 * skill/level thresholds, diary tiers, and combat achievements, for tasks
 * carrying a {@link TaskEntry.AutoDetectRule} -- and marks them, but only
 * when the player asks.
 *
 * <p><b>Nothing is written unprompted.</b> {@link #preview} counts what
 * would be marked and writes nothing; {@link #apply} is what commits, and
 * only the Sync card's confirmed button calls it. Detection is a heuristic
 * over wiki text and there is no un-mark, so a silent background pass would
 * be writing guesses into the player's progress behind their back.
 *
 * <p>The one thing that happens on its own is following the logged-in
 * account, so {@link CompletedTaskStore} reads the right character's list
 * -- see {@link #onGameStateChanged}. Reading which account is playing is
 * not the same as writing progress for it.
 *
 * <p>Threading: callers are on the EDT,
 * every client read happens on the client thread, and results come back on
 * the EDT via {@link SwingUtilities#invokeLater}, so callers need no thread
 * coordination.
 */
@Singleton
public class TaskAutoDetector
{
    private static final String NOT_LOGGED_IN = "Log in to a character to see what can be autocompleted.";

    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final TaskRepository repository;
    private final CompletedTaskStore completedTaskStore;
    private final SavedTaskStore savedTaskStore;
    private final HiscoresKillCatalog hiscoresKillCatalog;
    private final AutoDetectGameStateFactory gameStateFactory;

    private Runnable onAccountChanged;

    /** Built once per preview/apply pass on the client thread. */
    private AutoDetectGameState gameState;

    @Inject
    public TaskAutoDetector(Client client, ClientThread clientThread, EventBus eventBus,
        TaskRepository repository, CompletedTaskStore completedTaskStore,
        SavedTaskStore savedTaskStore,
        HiscoresKillCatalog hiscoresKillCatalog,
        AutoDetectGameStateFactory gameStateFactory)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.repository = repository;
        this.completedTaskStore = completedTaskStore;
        this.savedTaskStore = savedTaskStore;
        this.hiscoresKillCatalog = hiscoresKillCatalog;
        this.gameStateFactory = gameStateFactory;
    }

    public void startUp()
    {
        eventBus.register(this);
    }

    public void shutDown()
    {
        eventBus.unregister(this);
    }

    /**
     * Set by the panel so it can repaint when the store switches accounts.
     * Invoked on the client thread; the listener hops to the EDT itself.
     */
    public void setOnAccountChanged(Runnable listener)
    {
        this.onAccountChanged = listener;
    }

    /**
     * Points the completed-task store at whoever just logged in, so the
     * panel and any later autocomplete act on the right character's list.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Player player = client.getLocalPlayer();
        String name = player != null ? player.getName() : null;
        if (name == null || name.isEmpty())
        {
            // The display name is not populated the instant the state
            // flips. preview() and apply() both re-read the account before
            // touching the store.
            return;
        }

        boolean changed = completedTaskStore.setAccount(name);
        savedTaskStore.setAccount(name);
        if (changed && onAccountChanged != null)
        {
            onAccountChanged.run();
        }
    }

    /**
     * Counts what {@link #apply} would mark, writing nothing. Call from the
     * EDT; {@code callback} runs on the EDT exactly once.
     */
    public void preview(Consumer<AutocompletePreview> callback)
    {
        clientThread.invoke(() -> runWithGameState(() ->
        {
            AutocompletePreview preview = buildPreview();
            SwingUtilities.invokeLater(() -> callback.accept(preview));
        }, () -> SwingUtilities.invokeLater(() -> callback.accept(
            AutocompletePreview.unavailable(NOT_LOGGED_IN)))));
    }

    /**
     * Marks every currently-detectable task complete, recording each as
     * {@link CompletedTaskRecord.Source#AUTO}. Re-evaluates rather than
     * trusting an earlier preview, so a stale count can never cause a write
     * the player did not just see. Call from the EDT; {@code callback}
     * receives the number actually marked, on the EDT.
     */
    public void apply(Consumer<Integer> callback)
    {
        clientThread.invoke(() -> runWithGameState(() ->
        {
            int marked = 0;
            for (TaskEntry task : pendingTasks())
            {
                if (completedTaskStore.markCompleted(task, CompletedTaskRecord.Source.AUTO))
                {
                    marked++;
                }
            }

            int total = marked;
            SwingUtilities.invokeLater(() -> callback.accept(total));
        }, () -> SwingUtilities.invokeLater(() -> callback.accept(0))));
    }

    /**
     * Loads hiscores when needed, builds {@link #gameState} on the client
     * thread, then runs {@code whenReady}. {@code whenNotLoggedIn} runs when
     * there is no local player to read.
     */
    private void runWithGameState(Runnable whenReady, Runnable whenNotLoggedIn)
    {
        if (!isLoggedIn())
        {
            whenNotLoggedIn.run();
            return;
        }

        adoptCurrentAccount();
        Player player = client.getLocalPlayer();
        String username = player != null ? player.getName() : null;
        hiscoresKillCatalog.prefetch(username, snapshot -> clientThread.invoke(() ->
        {
            gameState = gameStateFactory.create(snapshot);
            whenReady.run();
        }));
    }

    private AutocompletePreview buildPreview()
    {
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        for (TaskEntry task : pendingTasks())
        {
            counts.merge(task.getAutoDetect().getType(), 1, Integer::sum);
            total++;
        }

        // RuleType.values() order is the breakdown's display order; a rule
        // whose type string matches no constant still gets a row under its
        // raw name, so a data file newer than this plugin cannot inflate the
        // total with counts the player can't see explained.
        List<AutocompletePreview.CategoryCount> categories = new ArrayList<>();
        for (RuleType type : RuleType.values())
        {
            Integer count = counts.remove(type.name());
            if (count != null && count > 0)
            {
                categories.add(new AutocompletePreview.CategoryCount(type.label(), count));
            }
        }
        for (Map.Entry<String, Integer> leftover : counts.entrySet())
        {
            categories.add(new AutocompletePreview.CategoryCount(leftover.getKey(), leftover.getValue()));
        }

        return AutocompletePreview.of(total, categories);
    }

    /**
     * Covers the case where the login event fired before the display name
     * was populated, so neither a count nor a write can land on the
     * previous character's list.
     */
    private void adoptCurrentAccount()
    {
        Player player = client.getLocalPlayer();
        if (player != null && player.getName() != null && !player.getName().isEmpty())
        {
            completedTaskStore.setAccount(player.getName());
            savedTaskStore.setAccount(player.getName());
        }
    }

    /**
     * Rule-bearing tasks whose rule is satisfied and which are not already
     * marked complete. Client thread only.
     */
    private List<TaskEntry> pendingTasks()
    {
        Set<String> alreadyDone = completedTaskStore.completedTaskIds();
        List<TaskEntry> pending = new ArrayList<>();
        for (TaskEntry task : repository.autoDetectableTasks())
        {
            if (!alreadyDone.contains(task.getId())
                && AutoDetectRuleEvaluators.isSatisfied(task.getAutoDetect(), gameState))
            {
                pending.add(task);
            }
        }
        return pending;
    }

    private boolean isLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
    }
}
