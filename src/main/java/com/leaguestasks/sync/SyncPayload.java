package com.leaguestasks.sync;

import com.leaguestasks.model.CompletedTaskRecord;
import java.util.List;
import lombok.Value;

/**
 * Request body sent to the configured sync endpoint. Each of
 * {@code completedTasks}/{@code quests}/{@code collectionLog}/
 * {@code achievementDiary}/{@code combatAchievements} may be null, in which
 * case Gson omits it from the JSON entirely (not sent as an empty value) --
 * see {@link ProgressSyncClient}'s section-specific sync methods, which each
 * populate only the section(s) they're syncing. The server keeps whatever it
 * stored last for any section a request omits, rather than treating the gap
 * as "cleared." Gson-serialized only; the generated getters are unused.
 */
@Value
public class SyncPayload
{
    String username;
    String accountHash;
    String syncedAt;
    List<CompletedTaskRecord> completedTasks;
    List<QuestProgress> quests;
    CollectionLogSnapshot collectionLog;
    List<DiaryProgress> achievementDiary;
    CombatAchievementSnapshot combatAchievements;
}
