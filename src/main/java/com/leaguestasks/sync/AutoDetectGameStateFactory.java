package com.leaguestasks.sync;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

@Singleton
public class AutoDetectGameStateFactory
{
    private final Client client;
    private final AchievementDiaryCatalog diaryCatalog;
    private final CombatAchievementCatalog combatCatalog;
    private final CombatAchievementIndex combatIndex;
    private final BossKillCatalog bossKillCatalog;
    private final ClueScrollCatalog clueScrollCatalog;
    private final QuestNameIndex questNameIndex;

    @Inject
    public AutoDetectGameStateFactory(Client client, AchievementDiaryCatalog diaryCatalog,
        CombatAchievementCatalog combatCatalog, CombatAchievementIndex combatIndex,
        BossKillCatalog bossKillCatalog, ClueScrollCatalog clueScrollCatalog,
        QuestNameIndex questNameIndex)
    {
        this.client = client;
        this.diaryCatalog = diaryCatalog;
        this.combatCatalog = combatCatalog;
        this.combatIndex = combatIndex;
        this.bossKillCatalog = bossKillCatalog;
        this.clueScrollCatalog = clueScrollCatalog;
        this.questNameIndex = questNameIndex;
    }

    /** Client thread only: reads diary and combat achievement varbits up front. */
    AutoDetectGameState create(HiscoresSnapshot hiscoresSnapshot)
    {
        return new AutoDetectGameState(diaryCatalog, combatCatalog, combatIndex,
            bossKillCatalog, clueScrollCatalog, questNameIndex, hiscoresSnapshot, client);
    }
}
