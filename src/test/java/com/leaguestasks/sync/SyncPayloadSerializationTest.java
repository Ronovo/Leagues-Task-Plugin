package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.leaguestasks.model.CompletedTaskRecord;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Serializes a real {@link SyncPayload} and checks the wire shape the tracker
 * expects -- and, crucially, that a section left null is omitted from the
 * JSON entirely, since the server merge-preserves any section a request does
 * not send.
 */
public class SyncPayloadSerializationTest
{
    private static final Gson GSON = new Gson();

    private static final List<String> OPTIONAL_SECTIONS = List.of(
        "completedTasks", "quests", "collectionLog", "achievementDiary", "combatAchievements");

    @Test
    public void fullPayloadSerializesEverySection()
    {
        SyncPayload payload = new SyncPayload(
            "testrsn",
            "1234567890123456789",
            "2026-08-29T12:00:00Z",
            Collections.singletonList(new CompletedTaskRecord("client-of-kourend", "2026-08-29T11:00:00Z",
                CompletedTaskRecord.Source.MANUAL)),
            Collections.singletonList(new QuestProgress(1, "Cook's Assistant", "FINISHED")),
            new CollectionLogSnapshot(1, 2, Collections.singletonList(
                new CollectionLogGroup("Bosses", Collections.singletonList(
                    new CollectionLogCategory("zulrah", "Zulrah", Collections.singletonList(
                        new CollectionLogItem(12921, "Tanzanite fang", 1))))))),
            Collections.singletonList(new DiaryProgress("ARDOUGNE", "ELITE", true)),
            new CombatAchievementSnapshot(100, Collections.singletonList(
                new CombatAchievementTask("ABYSSALSIRE_KILLCOUNT_1", true))));

        JsonObject body = GSON.fromJson(GSON.toJson(payload), JsonObject.class);

        assertEquals("testrsn", body.get("username").getAsString());
        assertEquals("1234567890123456789", body.get("accountHash").getAsString());
        assertEquals("2026-08-29T12:00:00Z", body.get("syncedAt").getAsString());

        assertEquals(1, body.getAsJsonArray("completedTasks").size());
        assertEquals("client-of-kourend",
            body.getAsJsonArray("completedTasks").get(0).getAsJsonObject().get("taskId").getAsString());
        assertEquals("MANUAL",
            body.getAsJsonArray("completedTasks").get(0).getAsJsonObject().get("source").getAsString());

        assertEquals(1, body.getAsJsonArray("quests").size());
        assertEquals("Cook's Assistant",
            body.getAsJsonArray("quests").get(0).getAsJsonObject().get("name").getAsString());

        JsonObject clog = body.getAsJsonObject("collectionLog");
        assertEquals(1, clog.get("itemsObtained").getAsInt());
        assertEquals("Bosses",
            clog.getAsJsonArray("groups").get(0).getAsJsonObject().get("group").getAsString());

        assertEquals("ELITE",
            body.getAsJsonArray("achievementDiary").get(0).getAsJsonObject().get("tier").getAsString());

        assertEquals(100, body.getAsJsonObject("combatAchievements").get("points").getAsInt());
        assertEquals("ABYSSALSIRE_KILLCOUNT_1", body.getAsJsonObject("combatAchievements")
            .getAsJsonArray("tasks").get(0).getAsJsonObject().get("task").getAsString());
    }

    @Test
    public void nullSectionsAreOmittedNotSentAsNull()
    {
        SyncPayload payload = new SyncPayload("u", "h", "2026-08-29T12:00:00Z",
            null, null, null, null, null);

        JsonObject body = GSON.fromJson(GSON.toJson(payload), JsonObject.class);

        assertTrue(body.has("username"));
        assertTrue(body.has("accountHash"));
        assertTrue(body.has("syncedAt"));
        for (String section : OPTIONAL_SECTIONS)
        {
            assertFalse(section + " must be omitted when null", body.has(section));
        }
    }

    @Test
    public void fullPayloadHasExactlyTheExpectedKeys()
    {
        SyncPayload payload = new SyncPayload("u", "h", "t",
            Collections.emptyList(), Collections.emptyList(),
            new CollectionLogSnapshot(0, 0, Collections.emptyList()),
            Collections.emptyList(),
            new CombatAchievementSnapshot(0, Collections.emptyList()));

        JsonObject body = GSON.fromJson(GSON.toJson(payload), JsonObject.class);

        assertEquals(
            java.util.Set.of("username", "accountHash", "syncedAt", "completedTasks", "quests",
                "collectionLog", "achievementDiary", "combatAchievements"),
            body.keySet());
    }
}
