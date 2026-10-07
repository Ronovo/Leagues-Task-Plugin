package com.leaguestasks.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.leaguestasks.LeaguesTasksConfig;
import com.leaguestasks.model.CompletedTaskRecord;
import com.leaguestasks.model.CompletedTaskStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProgressSyncClientTest
{
    private MockWebServer server;
    private Client client;
    private LeaguesTasksConfig config;
    private CompletedTaskStore completedTaskStore;
    private CollectionLogCapture collectionLogCapture;
    private AchievementDiaryCatalog achievementDiaryCatalog;
    private CombatAchievementCatalog combatAchievementCatalog;
    private ProgressSyncClient progressSyncClient;

    @Before
    public void setUp() throws Exception
    {
        server = new MockWebServer();
        server.start();

        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation ->
        {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestRSN");

        client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getAccountHash()).thenReturn(1234567890123456789L);
        // Quest.getState runs a clientscript and reads the int stack; a
        // mock returns null for it, so every quest reads NOT_STARTED here.
        when(client.getIntStack()).thenReturn(new int[]{1});

        config = mock(LeaguesTasksConfig.class);
        // Config stores a base URL; the client appends the sync path.
        when(config.serverUrl()).thenReturn(server.url("/").toString());
        when(config.apiToken()).thenReturn("test-token");

        completedTaskStore = mock(CompletedTaskStore.class);
        List<CompletedTaskRecord> records = Arrays.asList(
            new CompletedTaskRecord("task-a", "2026-01-01T00:00:00Z"),
            new CompletedTaskRecord("task-b", "2026-01-02T00:00:00Z"));
        when(completedTaskStore.allRecords()).thenReturn(records);

        // Collection log defaults to "interface closed, nothing cached",
        // the state every sync is in until the log has been dumped once.
        collectionLogCapture = mock(CollectionLogCapture.class);

        achievementDiaryCatalog = mock(AchievementDiaryCatalog.class);
        when(achievementDiaryCatalog.isAvailable()).thenReturn(true);
        when(achievementDiaryCatalog.build(client)).thenReturn(Arrays.asList(
            new DiaryProgress("ARDOUGNE", "ELITE", true)));

        combatAchievementCatalog = mock(CombatAchievementCatalog.class);
        when(combatAchievementCatalog.isAvailable()).thenReturn(true);
        when(combatAchievementCatalog.build(client)).thenReturn(
            new CombatAchievementSnapshot(100, Collections.singletonList(
                new CombatAchievementTask("ABYSSALSIRE_KILLCOUNT_1", true))));

        progressSyncClient = new ProgressSyncClient(
            new OkHttpClient(), new Gson(), clientThread, client, config, completedTaskStore,
            collectionLogCapture, achievementDiaryCatalog, combatAchievementCatalog);
    }

    @After
    public void tearDown() throws Exception
    {
        server.shutdown();
    }

    @Test
    public void success() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.SUCCESS, result.outcome);
        assertEquals("All of the above synced for testrsn.", result.message);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/sync/leagues", request.getPath());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));

        JsonObject body = parseBody(request);
        assertEquals("testrsn", body.get("username").getAsString());
        assertEquals("1234567890123456789", body.get("accountHash").getAsString());
        assertEquals(2, body.getAsJsonArray("completedTasks").size());
        assertTrue(body.has("achievementDiary"));
        assertEquals(1, body.getAsJsonArray("achievementDiary").size());
        assertTrue(body.has("combatAchievements"));
        assertEquals(100, body.getAsJsonObject("combatAchievements").get("points").getAsInt());
        // Omitted rather than sent empty, so the server keeps the log it
        // already has instead of overwriting it with nothing.
        assertFalse(body.has("collectionLog"));
    }

    @Test
    public void cachedCollectionLogIsSentWhenInterfaceIsClosed() throws Exception
    {
        when(collectionLogCapture.isCollectionLogOpen()).thenReturn(false);
        when(collectionLogCapture.cachedSnapshot()).thenReturn(snapshot());
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        JsonObject body = parseBody(server.takeRequest());
        JsonObject collectionLog = body.getAsJsonObject("collectionLog");
        assertEquals(1, collectionLog.get("itemsObtained").getAsInt());
        assertEquals(2, collectionLog.get("itemsAvailable").getAsInt());
        assertEquals(1, collectionLog.getAsJsonArray("groups").size());
    }

    @Test
    public void freshCollectionLogIsCapturedWhenInterfaceIsOpen() throws Exception
    {
        when(collectionLogCapture.isCollectionLogOpen()).thenReturn(true);
        doAnswer(invocation ->
        {
            invocation.<Consumer<CollectionLogSnapshot>>getArgument(0).accept(snapshot());
            return null;
        }).when(collectionLogCapture).capture(any());
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        JsonObject body = parseBody(server.takeRequest());
        assertEquals(1, body.getAsJsonObject("collectionLog").get("itemsObtained").getAsInt());
    }

    private static CollectionLogSnapshot snapshot()
    {
        List<CollectionLogItem> items = Arrays.asList(
            new CollectionLogItem(12934, "Toxic blowpipe", 1),
            new CollectionLogItem(12921, "Tanzanite fang", 0));
        return new CollectionLogSnapshot(1, 2, Collections.singletonList(
            new CollectionLogGroup("Bosses",
                Collections.singletonList(new CollectionLogCategory("zulrah", "Zulrah", items)))));
    }

    @Test
    public void unauthorized() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(401));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.UNAUTHORIZED, result.outcome);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void serverError() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(500));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.HTTP_ERROR, result.outcome);
    }

    @Test
    public void networkError() throws Exception
    {
        server.shutdown();

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.NETWORK_ERROR, result.outcome);
    }

    @Test
    public void missingConfigSkipsRequest() throws Exception
    {
        when(config.apiToken()).thenReturn("");

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.MISSING_CONFIG, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void invalidSchemeSkipsRequest() throws Exception
    {
        when(config.serverUrl()).thenReturn("file:///tmp/tracker");

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.MISSING_CONFIG, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void redirectIsNotFollowed() throws Exception
    {
        server.enqueue(new MockResponse()
            .setResponseCode(302)
            .addHeader("Location", "/elsewhere"));
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.HTTP_ERROR, result.outcome);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void notLoggedInSkipsRequest() throws Exception
    {
        when(client.getLocalPlayer()).thenReturn(null);

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.NOT_LOGGED_IN, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void missingAccountHashSkipsRequest() throws Exception
    {
        when(client.getAccountHash()).thenReturn(-1L);

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.NOT_LOGGED_IN, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void syncTasksSendsOnlyTasks() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncTasks);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);
        assertTrue(result.message.contains("testrsn"));
        assertFalse("tasks-only sync shouldn't mention quests", result.message.contains("quest"));

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/tasks", request.getPath());
        JsonObject body = parseBody(request);
        assertTrue(body.has("completedTasks"));
        assertEquals(2, body.getAsJsonArray("completedTasks").size());
        assertFalse(body.has("quests"));
        assertFalse(body.has("collectionLog"));
        assertFalse(body.has("achievementDiary"));
        assertFalse(body.has("combatAchievements"));
    }

    @Test
    public void syncTasksOmitsEmptyTaskList() throws Exception
    {
        when(completedTaskStore.allRecords()).thenReturn(Collections.emptyList());
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncTasks);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        JsonObject body = parseBody(server.takeRequest());
        assertEquals("testrsn", body.get("username").getAsString());
        assertFalse("empty local store must omit completedTasks so the server keeps prior data",
            body.has("completedTasks"));
    }

    @Test
    public void syncTasksMissingConfigSkipsRequest() throws Exception
    {
        when(config.apiToken()).thenReturn("");

        CapturedResult result = syncAndAwait(progressSyncClient::syncTasks);

        assertEquals(SyncOutcome.MISSING_CONFIG, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void syncQuestsSendsOnlyQuests() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncQuests);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);
        assertTrue(result.message.contains("testrsn"));
        assertFalse("quests-only sync shouldn't mention tasks", result.message.contains("task"));

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/quests", request.getPath());
        JsonObject body = parseBody(request);
        assertTrue(body.has("quests"));
        assertFalse(body.has("completedTasks"));
        assertFalse(body.has("collectionLog"));
        assertFalse(body.has("achievementDiary"));
        assertFalse(body.has("combatAchievements"));
    }

    @Test
    public void syncAchievementDiarySendsOnlyDiary() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncAchievementDiary);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);
        assertTrue(result.message.contains("achievement diary"));

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/diaries", request.getPath());
        JsonObject body = parseBody(request);
        assertTrue(body.has("achievementDiary"));
        assertEquals(1, body.getAsJsonArray("achievementDiary").size());
        assertFalse(body.has("completedTasks"));
        assertFalse(body.has("quests"));
        assertFalse(body.has("collectionLog"));
        assertFalse(body.has("combatAchievements"));
    }

    @Test
    public void syncCombatAchievementsSendsOnlyCombatAchievements() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncCombatAchievements);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);
        assertTrue(result.message.contains("CA point"));

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/combatachievements", request.getPath());
        JsonObject body = parseBody(request);
        assertTrue(body.has("combatAchievements"));
        assertEquals(100, body.getAsJsonObject("combatAchievements").get("points").getAsInt());
        assertFalse(body.has("completedTasks"));
        assertFalse(body.has("quests"));
        assertFalse(body.has("collectionLog"));
        assertFalse(body.has("achievementDiary"));
    }

    @Test
    public void unavailableCatalogOmitsSectionOnFullSync() throws Exception
    {
        when(achievementDiaryCatalog.isAvailable()).thenReturn(false);
        when(combatAchievementCatalog.isAvailable()).thenReturn(false);
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait();

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        JsonObject body = parseBody(server.takeRequest());
        assertFalse(body.has("achievementDiary"));
        assertFalse(body.has("combatAchievements"));
    }

    @Test
    public void syncQuestsMissingConfigSkipsRequest() throws Exception
    {
        when(config.apiToken()).thenReturn("");

        CapturedResult result = syncAndAwait(progressSyncClient::syncQuests);

        assertEquals(SyncOutcome.MISSING_CONFIG, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void syncCollectionLogCapturesFreshWhenOpen() throws Exception
    {
        when(collectionLogCapture.isCollectionLogOpen()).thenReturn(true);
        doAnswer(invocation ->
        {
            invocation.<Consumer<CollectionLogSnapshot>>getArgument(0).accept(snapshot());
            return null;
        }).when(collectionLogCapture).capture(any());
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncCollectionLog);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/collectionlog", request.getPath());
        JsonObject body = parseBody(request);
        assertTrue(body.has("collectionLog"));
        assertFalse(body.has("completedTasks"));
        assertFalse(body.has("quests"));
    }

    @Test
    public void syncCollectionLogSendsCachedWhenClosed() throws Exception
    {
        when(collectionLogCapture.isCollectionLogOpen()).thenReturn(false);
        when(collectionLogCapture.cachedSnapshot()).thenReturn(snapshot());
        server.enqueue(new MockResponse().setResponseCode(200));

        CapturedResult result = syncAndAwait(progressSyncClient::syncCollectionLog);

        assertEquals(SyncOutcome.SUCCESS, result.outcome);

        RecordedRequest request = server.takeRequest();
        assertEquals("/api/sync/collectionlog", request.getPath());
        JsonObject body = parseBody(request);
        assertEquals(1, body.getAsJsonObject("collectionLog").get("itemsObtained").getAsInt());
        assertFalse(body.has("completedTasks"));
        assertFalse(body.has("quests"));
    }

    @Test
    public void syncCollectionLogWithNothingAvailableSkipsRequest() throws Exception
    {
        when(collectionLogCapture.isCollectionLogOpen()).thenReturn(false);
        when(collectionLogCapture.cachedSnapshot()).thenReturn(null);

        CapturedResult result = syncAndAwait(progressSyncClient::syncCollectionLog);

        assertEquals(SyncOutcome.NO_COLLECTION_LOG, result.outcome);
        assertEquals(0, server.getRequestCount());
    }

    /**
     * A sync callback is delivered via SwingUtilities.invokeLater even for
     * the real OkHttp round trip, so the test has to wait for the EDT
     * rather than asserting immediately after the call returns.
     */
    private CapturedResult syncAndAwait() throws InterruptedException
    {
        return syncAndAwait(progressSyncClient::sync);
    }

    private CapturedResult syncAndAwait(Consumer<SyncCallback> action) throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SyncOutcome> outcomeRef = new AtomicReference<>();
        AtomicReference<String> messageRef = new AtomicReference<>();

        action.accept((outcome, message) ->
        {
            outcomeRef.set(outcome);
            messageRef.set(message);
            latch.countDown();
        });

        assertTrue("Sync callback did not fire in time", latch.await(5, TimeUnit.SECONDS));
        return new CapturedResult(outcomeRef.get(), messageRef.get());
    }

    private static JsonObject parseBody(RecordedRequest request) throws Exception
    {
        return new Gson().fromJson(request.getBody().readUtf8(), JsonObject.class);
    }

    private static final class CapturedResult
    {
        final SyncOutcome outcome;
        final String message;

        CapturedResult(SyncOutcome outcome, String message)
        {
            this.outcome = outcome;
            this.message = message;
        }
    }
}
