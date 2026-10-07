package com.leaguestasks.sync;

import com.leaguestasks.CountDiff;
import com.leaguestasks.model.RuleType;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards the {@code autoDetect} rules in leagues_tasks.json. A hand edit to
 * that file can quietly change them; this is what makes that loud.
 */
public class AutoDetectRuleIntegrityTest
{
    private static final Set<String> KNOWN_TYPES = Arrays.stream(RuleType.values())
        .map(Enum::name)
        .collect(Collectors.toSet());

    private static final Set<String> CLUE_TIERS = new HashSet<>(Arrays.asList(
        "EASY", "MEDIUM", "HARD", "ELITE", "MASTER"));

    private static final Set<String> CA_TIERS = new HashSet<>(Arrays.asList(
        "EASY", "MEDIUM", "HARD", "ELITE", "MASTER", "GRANDMASTER"));

    private static final Set<String> DIARY_TIERS = new HashSet<>(Arrays.asList(
        "EASY", "MEDIUM", "HARD", "ELITE"));

    /**
     * Per-type rule counts. An edit to leagues_tasks.json that moves these
     * deserves a look rather than sliding through -- update deliberately.
     */
    private static final Map<String, Integer> EXPECTED_COUNTS = new HashMap<>();

    static
    {
        EXPECTED_COUNTS.put("QUEST", 43);
        EXPECTED_COUNTS.put("SKILL_LEVEL", 138);
        EXPECTED_COUNTS.put("SKILL_XP", 23);
        EXPECTED_COUNTS.put("TOTAL_LEVEL", 24);
        EXPECTED_COUNTS.put("BASE_LEVEL", 20);
        EXPECTED_COUNTS.put("ANY_SKILL_LEVEL", 9);
        EXPECTED_COUNTS.put("COMBAT_LEVEL", 8);
        EXPECTED_COUNTS.put("DIARY_TIER", 48);
        EXPECTED_COUNTS.put("CA_POINTS", 12);
        EXPECTED_COUNTS.put("CA_COUNT", 9);
        EXPECTED_COUNTS.put("CA_TIER_COUNT", 31);
        EXPECTED_COUNTS.put("CA_TIER_ALL", 11);
        EXPECTED_COUNTS.put("CA_GROUP", 14);
        EXPECTED_COUNTS.put("BOSS_KILL", 218);
        EXPECTED_COUNTS.put("BOSS_KILL_ALL", 5);
        EXPECTED_COUNTS.put("BOSS_KILL_ANY", 3);
        EXPECTED_COUNTS.put("BOSS_KILL_HISCORES", 12);
        EXPECTED_COUNTS.put("CLUE_COUNT", 45);
    }

    /**
     * Wiki "quests" that RuneLite's {@link Quest} enum does not model, so
     * no amount of name-fixing will resolve them. Tasks keyed on these stay
     * hand-marked; they are listed here so a genuinely broken title cannot
     * hide among them.
     */
    private static final Set<String> UNMODELLED_BY_RUNELITE = new HashSet<>();

    private final TaskRepository repository = new TaskRepository();
    private final CombatAchievementCatalog combatCatalog = new CombatAchievementCatalog();
    private final BossKillCatalog bossKillCatalog = new BossKillCatalog();
    private final ClueScrollCatalog clueScrollCatalog = new ClueScrollCatalog();
    private final HiscoresKillCatalog hiscoresKillCatalog = new HiscoresKillCatalog(new okhttp3.OkHttpClient());
    private final Set<String> discoveredCaSlugs = new HashSet<>(combatCatalog.discoveredSlugs());

    @Test
    public void everyRuleIsWellFormed()
    {
        for (TaskEntry task : repository.autoDetectableTasks())
        {
            TaskEntry.AutoDetectRule rule = task.getAutoDetect();
            String where = task.getId() + " (" + task.getName() + ")";
            assertTrue(where + " has unknown rule type " + rule.getType(),
                KNOWN_TYPES.contains(rule.getType()));

            switch (rule.getType())
            {
                case "QUEST":
                    assertNotNull(where + " QUEST rule needs a quest name", rule.getQuest());
                    break;
                case "DIARY_TIER":
                    assertNotNull(where + " DIARY_TIER rule needs an area", rule.getArea());
                    assertNotNull(where + " DIARY_TIER rule needs a tier", rule.getTier());
                    assertTrue(where + " DIARY_TIER tier must be valid",
                        DIARY_TIERS.contains(rule.getTier().toUpperCase(Locale.ROOT)));
                    break;
                case "CA_TIER_ALL":
                    assertNotNull(where + " CA_TIER_ALL rule needs a tier", rule.getTier());
                    assertTrue(where + " CA tier must be valid",
                        CA_TIERS.contains(rule.getTier().toUpperCase(Locale.ROOT)));
                    break;
                case "CA_GROUP":
                    assertNotNull(where + " CA_GROUP rule needs groups", rule.getGroups());
                    assertFalse(where + " CA_GROUP rule needs at least one prefix",
                        rule.getGroups().isEmpty());
                    for (String prefix : rule.getGroups())
                    {
                        assertTrue(where + " prefix " + prefix + " matches no CA varbit slug",
                            prefixMatchesAnySlug(prefix));
                    }
                    break;
                case "CA_TIER_COUNT":
                    assertNotNull(where + " CA_TIER_COUNT rule needs a tier", rule.getTier());
                    assertTrue(where + " CA tier must be valid",
                        CA_TIERS.contains(rule.getTier().toUpperCase(Locale.ROOT)));
                    assertPositiveValue(where, rule);
                    break;
                case "BOSS_KILL":
                    assertNotNull(where + " BOSS_KILL rule needs a varp", rule.getVarp());
                    assertTrue(where + " BOSS_KILL varp must resolve",
                        bossKillCatalog.isVarpKnown(rule.getVarp()));
                    assertPositiveValue(where, rule);
                    break;
                case "BOSS_KILL_HISCORES":
                    assertNotNull(where + " BOSS_KILL_HISCORES rule needs a boss", rule.getBoss());
                    assertTrue(where + " BOSS_KILL_HISCORES boss must be known",
                        hiscoresKillCatalog.isBossKnown(rule.getBoss()));
                    assertPositiveValue(where, rule);
                    break;
                case "CLUE_COUNT":
                    assertNotNull(where + " CLUE_COUNT rule needs a tier", rule.getTier());
                    assertTrue(where + " CLUE_COUNT tier must be valid",
                        CLUE_TIERS.contains(rule.getTier().toUpperCase(Locale.ROOT)));
                    assertTrue(where + " CLUE_COUNT tier must resolve to a varbit",
                        clueScrollCatalog.isTierKnown(rule.getTier()));
                    assertPositiveValue(where, rule);
                    break;
                case "BOSS_KILL_ALL":
                case "BOSS_KILL_ANY":
                    assertNotNull(where + " rule needs varp groups", rule.getGroups());
                    assertFalse(where + " rule needs at least one varp", rule.getGroups().isEmpty());
                    for (String varp : rule.getGroups())
                    {
                        assertTrue(where + " varp " + varp + " must resolve",
                            bossKillCatalog.isVarpKnown(varp));
                    }
                    assertPositiveValue(where, rule);
                    break;
                default:
                    assertPositiveValue(where, rule);
                    if (rule.getType().startsWith("SKILL_"))
                    {
                        assertNotNull(where + " skill rule needs a skill", rule.getSkill());
                        Skill.valueOf(rule.getSkill());
                    }
                    break;
            }
        }
    }

    private static void assertPositiveValue(String where, TaskEntry.AutoDetectRule rule)
    {
        assertNotNull(where + " threshold rule needs a value", rule.getValue());
        assertTrue(where + " threshold must be positive", rule.getValue() > 0);
    }

    private boolean prefixMatchesAnySlug(String prefix)
    {
        String needle = prefix + "_";
        for (String slug : discoveredCaSlugs)
        {
            if (slug.startsWith(needle))
            {
                return true;
            }
        }
        return false;
    }

    @Test
    public void everyQuestRuleResolvesToARuneLiteQuest()
    {
        Set<String> known = new HashSet<>();
        for (Quest quest : Quest.values())
        {
            known.add(QuestNameIndex.normalize(quest.getName()));
        }

        List<String> unresolved = new ArrayList<>();
        for (TaskEntry task : repository.autoDetectableTasks())
        {
            TaskEntry.AutoDetectRule rule = task.getAutoDetect();
            if ("QUEST".equals(rule.getType())
                && !UNMODELLED_BY_RUNELITE.contains(rule.getQuest())
                && !known.contains(QuestNameIndex.normalize(rule.getQuest())))
            {
                unresolved.add(rule.getQuest());
            }
        }

        assertTrue("quest titles with no matching RuneLite Quest constant: " + unresolved,
            unresolved.isEmpty());
    }

    @Test
    public void ruleCountsAreUnchanged()
    {
        Map<String, Integer> actual = new HashMap<>();
        for (TaskEntry task : repository.autoDetectableTasks())
        {
            actual.merge(task.getAutoDetect().getType(), 1, Integer::sum);
        }

        assertEquals("rule count per type changed (expected -> actual):"
            + CountDiff.describe(EXPECTED_COUNTS, actual), EXPECTED_COUNTS, actual);
    }

    /**
     * The pinned counts and {@link RuleType} must stay 1:1 -- so adding a
     * rule type forces a deliberate edit here rather than sliding through.
     */
    @Test
    public void expectedCountsMatchTheRuleTypeEnum()
    {
        Set<String> ruleTypeNames = Arrays.stream(RuleType.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(ruleTypeNames, EXPECTED_COUNTS.keySet());
    }

    @Test
    public void everyRuleTypeHasAnEvaluator()
    {
        assertEquals(EnumSet.allOf(RuleType.class), AutoDetectRuleEvaluators.supportedTypes());
    }

    @Test
    public void everyShippedRuleParsesToARuleType()
    {
        for (TaskEntry task : repository.autoDetectableTasks())
        {
            String where = task.getId() + " (" + task.getName() + ")";
            assertNotNull(where + " has unparseable rule type " + task.getAutoDetect().getType(),
                task.getAutoDetect().getRuleType());
        }
    }
}
