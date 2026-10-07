package com.leaguestasks.sync;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Everything an autocomplete pass reads, behind one object the
 * {@link AutoDetectRuleEvaluators} query. Diary and combat achievement state
 * is read once up front; skills, quests and kill counts are read on demand.
 * Built by {@link AutoDetectGameStateFactory} on the client thread and only
 * used there.
 */
final class AutoDetectGameState
{
    /**
     * The 23 trainable skills, in the sense the Leagues tasks mean.
     * Deliberately not {@link Skill#values()}: that also carries
     * {@code OVERALL}, which would wreck a min/max, and {@code SAILING},
     * which no task from these leagues can refer to and which would peg
     * every "Base Level N" rule at 1 forever.
     */
    private static final List<Skill> TRAINABLE_SKILLS = Collections.unmodifiableList(Arrays.asList(
        Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC,
        Skill.RUNECRAFT, Skill.CONSTRUCTION, Skill.HITPOINTS, Skill.AGILITY, Skill.HERBLORE,
        Skill.THIEVING, Skill.CRAFTING, Skill.FLETCHING, Skill.SLAYER, Skill.HUNTER, Skill.MINING,
        Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
        Skill.FARMING));

    private final Map<String, Boolean> diaryComplete;
    private final CombatAchievementSnapshot combatAchievements;
    private final Map<String, Boolean> combatCompleteBySlug;
    private final CombatAchievementIndex combatIndex;
    private final BossKillCatalog bossKillCatalog;
    private final ClueScrollCatalog clueScrollCatalog;
    private final QuestNameIndex questNameIndex;
    private final HiscoresSnapshot hiscoresSnapshot;
    private final Client client;

    AutoDetectGameState(
        AchievementDiaryCatalog diaryCatalog,
        CombatAchievementCatalog combatCatalog,
        CombatAchievementIndex combatIndex,
        BossKillCatalog bossKillCatalog,
        ClueScrollCatalog clueScrollCatalog,
        QuestNameIndex questNameIndex,
        HiscoresSnapshot hiscoresSnapshot,
        Client client)
    {
        this.combatIndex = combatIndex;
        this.bossKillCatalog = bossKillCatalog;
        this.clueScrollCatalog = clueScrollCatalog;
        this.questNameIndex = questNameIndex;
        this.hiscoresSnapshot = hiscoresSnapshot != null ? hiscoresSnapshot : HiscoresSnapshot.empty();
        this.client = client;
        this.diaryComplete = new HashMap<>();
        if (diaryCatalog.isAvailable())
        {
            for (DiaryProgress entry : diaryCatalog.build(client))
            {
                diaryComplete.put(key(entry.getArea(), entry.getTier()), entry.isComplete());
            }
        }

        if (combatCatalog.isAvailable())
        {
            this.combatAchievements = combatCatalog.build(client);
        }
        else
        {
            this.combatAchievements = null;
        }

        this.combatCompleteBySlug = new HashMap<>();
        if (combatAchievements != null)
        {
            for (CombatAchievementTask task : combatAchievements.getTasks())
            {
                combatCompleteBySlug.put(task.getTask(), task.isComplete());
            }
        }
    }

    boolean isQuestFinished(String questTitle)
    {
        Quest quest = questNameIndex.find(questTitle);
        return quest != null && quest.getState(client) == QuestState.FINISHED;
    }

    /** Real (unboosted) level, or -1 for a skill name RuneLite doesn't know. */
    int skillLevel(String skillName)
    {
        Skill skill = resolveSkill(skillName);
        return skill != null ? client.getRealSkillLevel(skill) : -1;
    }

    /** Experience, or -1 for a skill name RuneLite doesn't know. */
    int skillXp(String skillName)
    {
        Skill skill = resolveSkill(skillName);
        return skill != null ? client.getSkillExperience(skill) : -1;
    }

    int totalLevel()
    {
        return client.getTotalLevel();
    }

    int lowestSkillLevel()
    {
        int lowest = Integer.MAX_VALUE;
        for (Skill skill : TRAINABLE_SKILLS)
        {
            lowest = Math.min(lowest, client.getRealSkillLevel(skill));
        }
        return lowest;
    }

    int highestSkillLevel()
    {
        int highest = 0;
        for (Skill skill : TRAINABLE_SKILLS)
        {
            highest = Math.max(highest, client.getRealSkillLevel(skill));
        }
        return highest;
    }

    /** Combat level, or -1 when there is no local player. */
    int combatLevel()
    {
        Player player = client.getLocalPlayer();
        return player != null ? player.getCombatLevel() : -1;
    }

    boolean isDiaryTierComplete(String area, String tier)
    {
        if (area == null || tier == null)
        {
            return false;
        }
        Boolean complete = diaryComplete.get(key(area, tier.toUpperCase(Locale.ROOT)));
        return complete != null && complete;
    }

    boolean hasCombatPoints(long required)
    {
        return combatAchievements != null && combatAchievements.getPoints() >= required;
    }

    int completedCombatCount()
    {
        if (combatAchievements == null)
        {
            return 0;
        }

        int count = 0;
        for (CombatAchievementTask task : combatAchievements.getTasks())
        {
            if (task.isComplete())
            {
                count++;
            }
        }
        return count;
    }

    int completedCombatCountForTier(String tier)
    {
        if (combatAchievements == null || tier == null)
        {
            return 0;
        }

        Set<String> indexed = combatIndex.slugsForTier(tier);
        if (indexed.isEmpty())
        {
            return 0;
        }

        int count = 0;
        for (String slug : indexed)
        {
            if (Boolean.TRUE.equals(combatCompleteBySlug.get(slug)))
            {
                count++;
            }
        }
        return count;
    }

    boolean allIndexedCombatTierComplete(String tier)
    {
        if (combatAchievements == null || tier == null)
        {
            return false;
        }

        Set<String> indexed = combatIndex.slugsForTier(tier);
        if (indexed.isEmpty())
        {
            return false;
        }

        for (String slug : indexed)
        {
            if (!Boolean.TRUE.equals(combatCompleteBySlug.get(slug)))
            {
                return false;
            }
        }
        return true;
    }

    boolean allGroupPrefixesComplete(List<String> prefixes)
    {
        return CombatAchievementCatalog.allPrefixesComplete(combatAchievements, prefixes);
    }

    boolean bossKillMeetsThreshold(String varpFieldName, long required)
    {
        return bossKillCatalog != null
            && bossKillCatalog.meetsThreshold(client, varpFieldName, required);
    }

    boolean allBossKillsMeetThreshold(List<String> varpFieldNames, long required)
    {
        return bossKillCatalog != null
            && bossKillCatalog.allMeetThreshold(client, varpFieldNames, required);
    }

    boolean anyBossKillMeetsThreshold(List<String> varpFieldNames, long required)
    {
        return bossKillCatalog != null
            && bossKillCatalog.anyMeetsThreshold(client, varpFieldNames, required);
    }

    boolean hiscoresBossMeetsThreshold(String bossName, long required)
    {
        return hiscoresSnapshot.meetsThreshold(bossName, required);
    }

    boolean clueTierMeetsThreshold(String tier, long required)
    {
        return clueScrollCatalog != null
            && clueScrollCatalog.meetsThreshold(client, tier, required);
    }

    private static Skill resolveSkill(String name)
    {
        if (name == null)
        {
            return null;
        }

        try
        {
            return Skill.valueOf(name.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private static String key(String area, String tier)
    {
        return area + "|" + tier;
    }
}
