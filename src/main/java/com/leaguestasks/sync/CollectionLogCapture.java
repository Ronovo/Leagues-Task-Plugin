package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.leaguestasks.ConfigKeys;
import com.leaguestasks.model.AccountScopedConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures which collection log slots the player has filled.
 *
 * <p>There's no API for this the way there is for quests: the game never
 * sends the whole log to the client. The inventory behind the currently
 * drawn page ({@code inv_620}) only holds that page, so reading it dumps
 * Abyssal Sire -- or whoever is on screen -- and nothing else. The search
 * is what enumerates every obtained item: it fires script 4100 once per
 * slot, with the same inventory id {@code collection_draw_log} uses for
 * opacity (dummy ids included). {@link #capture} runs that search, and
 * {@link CollectionLogCatalog} matches each id per page slot.
 *
 * <p>Because that requires the interface to be open, the last successful
 * dump is cached in the RuneLite config so an ordinary sidebar sync can
 * still send a collection log.
 *
 * <p>Everything here runs on the client thread (events are delivered there
 * and the capture reads the cache), including the callback.
 */
@Singleton
public class CollectionLogCapture
{
    private static final Logger log = LoggerFactory.getLogger(CollectionLogCapture.class);

    /** Fired once per obtained item while the collection log search runs. */
    private static final int SEARCH_RESULT_SCRIPT = 4100;
    /** Runs the collection log's search, which enumerates every slot. */
    private static final int RUN_SEARCH_SCRIPT = 2240;
    /** Ticks of silence from the result script before the dump is done. */
    private static final int QUIET_TICKS = 2;
    /** Give up if the search never reports anything (empty or blocked log). */
    private static final int TIMEOUT_TICKS = 10;

    private final Client client;
    private final AccountScopedConfig scope;
    private final EventBus eventBus;
    private final CollectionLogCatalog catalog;

    private Set<Integer> obtainedIds;
    private Consumer<CollectionLogSnapshot> pending;
    private int lastResultTick;
    private int ticksWaited;

    @Inject
    public CollectionLogCapture(Client client, ConfigManager configManager, Gson gson, EventBus eventBus,
        CollectionLogCatalog catalog)
    {
        this.client = client;
        this.scope = new AccountScopedConfig(configManager, gson);
        this.eventBus = eventBus;
        this.catalog = catalog;
    }

    public void startUp()
    {
        eventBus.register(this);
    }

    public void shutDown()
    {
        eventBus.unregister(this);
        // Nothing will finish the dump once we stop receiving ticks, so
        // release any caller still waiting on one.
        if (pending != null)
        {
            Consumer<CollectionLogSnapshot> callback = pending;
            reset();
            callback.accept(cachedSnapshot());
        }
    }

    /** Points the cache at one account's dump, mirroring {@link com.leaguestasks.model.CompletedTaskStore}. */
    public void setAccount(String displayName)
    {
        scope.setAccount(displayName);
    }

    /** Client thread. True when the collection log interface is open. */
    public boolean isCollectionLogOpen()
    {
        return searchToggle() != null;
    }

    /** The last dump we took, or null if the log has never been synced. */
    public CollectionLogSnapshot cachedSnapshot()
    {
        String key = scope.storageKey(ConfigKeys.COLLECTION_LOG_PREFIX);
        return scope.read(key, CollectionLogSnapshot.class, () -> null);
    }

    /**
     * Runs the collection log search and reports the resulting snapshot.
     * Call on the client thread with the interface open; {@code onDone} is
     * invoked on the client thread exactly once, with the cached snapshot
     * (possibly null) if the dump can't be taken.
     */
    public void capture(Consumer<CollectionLogSnapshot> onDone)
    {
        Widget searchToggle = searchToggle();
        if (searchToggle == null || pending != null)
        {
            onDone.accept(cachedSnapshot());
            return;
        }

        obtainedIds = new HashSet<>();
        pending = onDone;
        lastResultTick = -1;
        ticksWaited = 0;

        client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(RUN_SEARCH_SCRIPT);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (pending == null || event.getScriptId() != SEARCH_RESULT_SCRIPT)
        {
            return;
        }

        Object[] args = event.getScriptEvent().getArguments();
        if (args != null && args.length > 1 && args[1] instanceof Integer)
        {
            obtainedIds.add((Integer) args[1]);
        }
        lastResultTick = client.getTickCount();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pending == null)
        {
            return;
        }

        ticksWaited++;
        boolean settled = lastResultTick != -1 && client.getTickCount() >= lastResultTick + QUIET_TICKS;
        if (!settled && ticksWaited < TIMEOUT_TICKS)
        {
            return;
        }

        Set<Integer> captured = obtainedIds;
        Consumer<CollectionLogSnapshot> callback = pending;
        reset();

        // A timeout means the search never reported anything, so an empty
        // result would wrongly read as "log fully cleared out".
        CollectionLogSnapshot snapshot = null;
        if (settled)
        {
            try
            {
                snapshot = catalog.build(captured);
            }
            catch (RuntimeException e)
            {
                // Unexpected game-cache shape: don't let this throw out of
                // the event handler and repeat every tick -- fall back to
                // the last good dump instead.
                log.warn("Collection log read failed; using the last cached dump", e);
            }
        }
        if (snapshot == null)
        {
            callback.accept(cachedSnapshot());
            return;
        }

        String key = scope.storageKey(ConfigKeys.COLLECTION_LOG_PREFIX);
        if (key != null)
        {
            scope.write(key, snapshot);
        }
        callback.accept(snapshot);
    }

    private Widget searchToggle()
    {
        return client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
    }

    private void reset()
    {
        obtainedIds = null;
        pending = null;
        lastResultTick = -1;
        ticksWaited = 0;
    }
}
