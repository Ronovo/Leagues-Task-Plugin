package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.leaguestasks.LeaguesTasksConfig;
import com.leaguestasks.model.CompletedTaskRecord;
import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.ui.TextUtil;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Posts progress to the configured sync endpoint, authenticated with a
 * Bearer token. Six independent entry points, each sending only the
 * section(s) they name -- {@link #sync} (everything), {@link #syncTasks},
 * {@link #syncQuests}, {@link #syncCollectionLog},
 * {@link #syncAchievementDiary}, and {@link #syncCombatAchievements} -- since
 * the server merge-preserves whatever a request omits (see
 * {@link SyncPayload}). Internally they all funnel through one
 * {@link Section}-driven path. Never blocks the Swing EDT or the RuneLite
 * client thread: reading the player's name and every {@link Quest}'s state
 * both require the client thread, the HTTP call itself runs on OkHttp's own
 * dispatcher threads, and every result -- success or failure -- is delivered
 * back on the EDT via the supplied {@link SyncCallback}, exactly once.
 *
 * <p>The collection log is the one section that can't just be read: it needs
 * the in-game interface open so {@link CollectionLogCapture} can run its
 * search. When the interface is closed, a sync that wants the collection log
 * falls back to the last cached dump -- or, for {@link #syncCollectionLog}
 * specifically, tells the caller there's nothing to send yet.
 *
 * <p>Only {@code https://} (or {@code http://} to localhost) is accepted --
 * see {@link SyncUrl}. A non-localhost {@code http://} URL is refused so the
 * token is never sent in cleartext.
 */
@Singleton
public class ProgressSyncClient
{
    private static final Logger log = LoggerFactory.getLogger(ProgressSyncClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_LOGGED_BODY_CHARS = 500;
    private static final int HTTP_UNAUTHORIZED = 401;

    private static final String SYNC_LEAGUES_PATH = "/api/sync/leagues";
    private static final String SYNC_TASKS_PATH = "/api/sync/tasks";
    private static final String SYNC_QUESTS_PATH = "/api/sync/quests";
    private static final String SYNC_COLLECTION_LOG_PATH = "/api/sync/collectionlog";
    private static final String SYNC_ACHIEVEMENT_DIARY_PATH = "/api/sync/diaries";
    private static final String SYNC_COMBAT_ACHIEVEMENTS_PATH = "/api/sync/combatachievements";

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ClientThread clientThread;
    private final Client client;
    private final LeaguesTasksConfig config;
    private final CompletedTaskStore completedTaskStore;
    private final CollectionLogCapture collectionLogCapture;
    private final AchievementDiaryCatalog achievementDiaryCatalog;
    private final CombatAchievementCatalog combatAchievementCatalog;

    @Inject
    public ProgressSyncClient(OkHttpClient okHttpClient, Gson gson, ClientThread clientThread, Client client,
        LeaguesTasksConfig config, CompletedTaskStore completedTaskStore,
        CollectionLogCapture collectionLogCapture, AchievementDiaryCatalog achievementDiaryCatalog,
        CombatAchievementCatalog combatAchievementCatalog)
    {
        this.okHttpClient = okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
        this.gson = gson;
        this.clientThread = clientThread;
        this.client = client;
        this.config = config;
        this.completedTaskStore = completedTaskStore;
        this.collectionLogCapture = collectionLogCapture;
        this.achievementDiaryCatalog = achievementDiaryCatalog;
        this.combatAchievementCatalog = combatAchievementCatalog;
    }

    /** One data section a request can carry. */
    private enum Part
    {
        TASKS, QUESTS, COLLECTION_LOG, DIARY, COMBAT_ACHIEVEMENTS
    }

    /**
     * The single source of truth for {@code {sync path, which sections,
     * success message}}. Adding a seventh section is a new {@code Part}, a
     * new constant here, and a builder line in {@link #onClientThread}.
     */
    private enum Section
    {
        FULL(SYNC_LEAGUES_PATH, EnumSet.allOf(Part.class))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "All of the above synced for " + id.username + ".";
            }
        },
        TASKS(SYNC_TASKS_PATH, EnumSet.of(Part.TASKS))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "Synced " + d.records.size() + " completed task(s) as " + id.username + ".";
            }
        },
        QUESTS(SYNC_QUESTS_PATH, EnumSet.of(Part.QUESTS))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "Synced " + d.quests.size() + " quest(s) as " + id.username + ".";
            }
        },
        COLLECTION_LOG(SYNC_COLLECTION_LOG_PATH, EnumSet.of(Part.COLLECTION_LOG))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "Synced " + describeCollectionLog(d.collectionLog) + " as " + id.username + ".";
            }
        },
        ACHIEVEMENT_DIARY(SYNC_ACHIEVEMENT_DIARY_PATH, EnumSet.of(Part.DIARY))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "Synced " + describeAchievementDiary(d.diary) + " as " + id.username + ".";
            }
        },
        COMBAT_ACHIEVEMENTS(SYNC_COMBAT_ACHIEVEMENTS_PATH, EnumSet.of(Part.COMBAT_ACHIEVEMENTS))
        {
            @Override
            String successMessage(SyncIdentity id, PayloadDraft d)
            {
                return "Synced " + describeCombatAchievements(d.combatAchievements) + " as " + id.username + ".";
            }
        };

        final String path;
        final EnumSet<Part> parts;

        Section(String path, EnumSet<Part> parts)
        {
            this.path = path;
            this.parts = parts;
        }

        abstract String successMessage(SyncIdentity id, PayloadDraft d);
    }

    /** Sends every sync section as one snapshot. Call from the EDT. */
    public void sync(SyncCallback callback)
    {
        start(Section.FULL, callback);
    }

    /** Sends only completed tasks -- all other sections are left untouched server-side. */
    public void syncTasks(SyncCallback callback)
    {
        start(Section.TASKS, callback);
    }

    /** Sends only quest progress -- all other sections are left untouched server-side. */
    public void syncQuests(SyncCallback callback)
    {
        start(Section.QUESTS, callback);
    }

    /**
     * Sends only the collection log -- all other sections are left untouched
     * server-side. Captures a fresh dump if the in-game interface happens to
     * be open; otherwise falls back to the last cached dump. If neither is
     * available, calls back with {@link SyncOutcome#NO_COLLECTION_LOG} and
     * never touches the network.
     */
    public void syncCollectionLog(SyncCallback callback)
    {
        start(Section.COLLECTION_LOG, callback);
    }

    /** Sends only achievement diary tier progress -- all other sections are left untouched server-side. */
    public void syncAchievementDiary(SyncCallback callback)
    {
        start(Section.ACHIEVEMENT_DIARY, callback);
    }

    /** Sends only combat achievement progress -- all other sections are left untouched server-side. */
    public void syncCombatAchievements(SyncCallback callback)
    {
        start(Section.COMBAT_ACHIEVEMENTS, callback);
    }

    private void start(Section section, SyncCallback callback)
    {
        String base = config.serverUrl();
        String token = config.apiToken();
        if (TextUtil.isBlank(base) || TextUtil.isBlank(token))
        {
            complete(callback, SyncOutcome.MISSING_CONFIG,
                "Set a server URL and token in the plugin settings first.");
            return;
        }

        String normalizedBase = SyncUrl.parse(base);
        if (normalizedBase == null)
        {
            complete(callback, SyncOutcome.MISSING_CONFIG,
                "Server URL must be https://, or http:// only for localhost (for example http://localhost:4123).");
            return;
        }

        String url = syncEndpoint(normalizedBase, section.path);
        String bearer = token.trim();
        clientThread.invoke(() -> onClientThread(section, url, bearer, callback));
    }

    private void onClientThread(Section section, String url, String token, SyncCallback callback)
    {
        SyncIdentity identity = requireIdentity(callback);
        if (identity == null)
        {
            return;
        }

        PayloadDraft draft = new PayloadDraft(identity);
        if (section.parts.contains(Part.TASKS))
        {
            draft.records = completedTaskStore.allRecords();
        }
        if (section.parts.contains(Part.QUESTS))
        {
            draft.quests = buildQuestProgress();
        }
        if (section.parts.contains(Part.DIARY))
        {
            draft.diary = buildAchievementDiary();
        }
        if (section.parts.contains(Part.COMBAT_ACHIEVEMENTS))
        {
            draft.combatAchievements = buildCombatAchievements();
        }

        if (section.parts.contains(Part.COLLECTION_LOG))
        {
            resolveCollectionLog(section, url, token, draft, callback);
            return;
        }

        send(section, url, token, draft, callback);
    }

    private void resolveCollectionLog(Section section, String url, String token, PayloadDraft draft,
        SyncCallback callback)
    {
        if (collectionLogCapture.isCollectionLogOpen())
        {
            // Takes a few ticks; capture() calls back on the client thread.
            collectionLogCapture.capture(snapshot ->
            {
                draft.collectionLog = snapshot;
                send(section, url, token, draft, callback);
            });
            return;
        }

        CollectionLogSnapshot cached = collectionLogCapture.cachedSnapshot();
        if (cached == null && section == Section.COLLECTION_LOG)
        {
            complete(callback, SyncOutcome.NO_COLLECTION_LOG,
                "No collection log captured yet -- open your Collection Log in game and try again.");
            return;
        }

        draft.collectionLog = cached;
        send(section, url, token, draft, callback);
    }

    private void send(Section section, String url, String token, PayloadDraft draft, SyncCallback callback)
    {
        post(url, token, draft.toPayload(), section.successMessage(draft.identity, draft), callback);
    }

    private void post(String url, String token, SyncPayload payload, String successMessage, SyncCallback callback)
    {
        Request request;
        try
        {
            RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
            request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();
        }
        catch (IllegalArgumentException e)
        {
            // Request.Builder.url() throws this for a malformed URL --
            // caught here so a bad config value can't escape as an
            // uncaught exception on the client thread.
            complete(callback, SyncOutcome.MISSING_CONFIG, "Server URL in plugin settings is not a valid URL.");
            return;
        }

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Progress sync failed", e);
                complete(callback, SyncOutcome.NETWORK_ERROR, "Sync failed: could not reach the server.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (Response r = response)
                {
                    if (r.code() == HTTP_UNAUTHORIZED)
                    {
                        complete(callback, SyncOutcome.UNAUTHORIZED,
                            "Sync failed: token was rejected (401). Check your token in settings.");
                    }
                    else if (!r.isSuccessful())
                    {
                        // The status alone doesn't say *why* the server
                        // rejected the payload; its response body usually
                        // does, so it is logged for diagnosis -- truncated
                        // and control-stripped so a hostile server can't
                        // forge log lines.
                        log.warn("Progress sync failed with HTTP {}: {}", r.code(), readBodySafely(r));
                        complete(callback, SyncOutcome.HTTP_ERROR, "Sync failed: server returned HTTP " + r.code() + ".");
                    }
                    else
                    {
                        complete(callback, SyncOutcome.SUCCESS, successMessage);
                    }
                }
            }
        });
    }

    private static String describeCollectionLog(CollectionLogSnapshot snapshot)
    {
        return snapshot == null
            ? "no collection log"
            : snapshot.getItemsObtained() + " collection log item(s)";
    }

    private static String describeAchievementDiary(List<DiaryProgress> achievementDiary)
    {
        return achievementDiary == null
            ? "no achievement diary"
            : achievementDiary.size() + " achievement diary tier(s)";
    }

    private static String describeCombatAchievements(CombatAchievementSnapshot combatAchievements)
    {
        if (combatAchievements == null)
        {
            return "no combat achievements";
        }
        return combatAchievements.getPoints() + " CA point(s) across "
            + combatAchievements.getTasks().size() + " task(s)";
    }

    private List<QuestProgress> buildQuestProgress()
    {
        List<QuestProgress> quests = new ArrayList<>(Quest.values().length);
        for (Quest quest : Quest.values())
        {
            if (quest.getId() < 0 || quest.getName().startsWith("Fake"))
            {
                continue;
            }
            quests.add(new QuestProgress(quest.getId(), quest.getName(), quest.getState(client).name()));
        }
        return quests;
    }

    private List<DiaryProgress> buildAchievementDiary()
    {
        if (!achievementDiaryCatalog.isAvailable())
        {
            return null;
        }
        return emptyToNull(achievementDiaryCatalog.build(client));
    }

    private CombatAchievementSnapshot buildCombatAchievements()
    {
        if (!combatAchievementCatalog.isAvailable())
        {
            return null;
        }
        return combatAchievementCatalog.build(client);
    }

    private SyncIdentity requireIdentity(SyncCallback callback)
    {
        Player player = client.getLocalPlayer();
        String username = player != null ? TextUtil.normalizeDisplayName(player.getName()) : null;
        if (TextUtil.isBlank(username))
        {
            complete(callback, SyncOutcome.NOT_LOGGED_IN, "Log in to a character before syncing.");
            return null;
        }
        long hash = client.getAccountHash();
        if (hash == -1L)
        {
            complete(callback, SyncOutcome.NOT_LOGGED_IN, "Log in to a character before syncing.");
            return null;
        }
        return new SyncIdentity(username, Long.toString(hash));
    }

    static String readBodySafely(Response r)
    {
        try
        {
            return sanitizeForLog(r.body() != null ? r.body().string() : "(no body)");
        }
        catch (IOException e)
        {
            return "(failed to read body: " + e.getMessage() + ")";
        }
    }

    /**
     * Truncates to {@value #MAX_LOGGED_BODY_CHARS} chars and replaces ASCII
     * control characters with spaces, so a hostile tracker cannot inject
     * newlines or terminal escapes into the client log.
     */
    static String sanitizeForLog(String body)
    {
        if (body == null)
        {
            return "(no body)";
        }
        String clipped = body.length() > MAX_LOGGED_BODY_CHARS
            ? body.substring(0, MAX_LOGGED_BODY_CHARS) + "... (truncated)"
            : body;
        StringBuilder out = new StringBuilder(clipped.length());
        for (int i = 0; i < clipped.length(); i++)
        {
            char c = clipped.charAt(i);
            out.append(c < 0x20 || c == 0x7f ? ' ' : c);
        }
        return out.toString();
    }

    /**
     * Joins the configured server URL with the sync path, tolerating a
     * trailing slash on the configured value.
     */
    private static String syncEndpoint(String serverUrl, String syncPath)
    {
        String base = serverUrl.trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base + syncPath;
    }

    private static <T> List<T> emptyToNull(List<T> values)
    {
        return values == null || values.isEmpty() ? null : values;
    }

    private void complete(SyncCallback callback, SyncOutcome outcome, String message)
    {
        SwingUtilities.invokeLater(() -> callback.onResult(outcome, message));
    }

    private static final class SyncIdentity
    {
        final String username;
        final String accountHash;

        SyncIdentity(String username, String accountHash)
        {
            this.username = username;
            this.accountHash = accountHash;
        }
    }

    private static final class PayloadDraft
    {
        final SyncIdentity identity;
        List<CompletedTaskRecord> records;
        List<QuestProgress> quests;
        CollectionLogSnapshot collectionLog;
        List<DiaryProgress> diary;
        CombatAchievementSnapshot combatAchievements;

        PayloadDraft(SyncIdentity identity)
        {
            this.identity = identity;
        }

        SyncPayload toPayload()
        {
            return new SyncPayload(
                identity.username,
                identity.accountHash,
                Instant.now().toString(),
                emptyToNull(records),
                emptyToNull(quests),
                collectionLog,
                emptyToNull(diary),
                combatAchievements);
        }
    }
}
