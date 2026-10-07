package com.leaguestasks.sync;

import com.leaguestasks.model.RuleType;
import com.leaguestasks.model.TaskEntry;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * One evaluator per {@link RuleType}: whether a rule is satisfied by the
 * game state read for this pass. A rule type with no entry here can never
 * be detected, which {@code AutoDetectRuleIntegrityTest} guards against.
 */
final class AutoDetectRuleEvaluators
{
    @FunctionalInterface
    interface RuleEvaluator
    {
        boolean isSatisfied(TaskEntry.AutoDetectRule rule, AutoDetectGameState state);
    }

    @FunctionalInterface
    private interface Threshold
    {
        boolean meets(TaskEntry.AutoDetectRule rule, AutoDetectGameState state, long required);
    }

    private static final Map<RuleType, RuleEvaluator> EVALUATORS;

    static
    {
        Map<RuleType, RuleEvaluator> m = new EnumMap<>(RuleType.class);
        m.put(RuleType.QUEST, (rule, s) -> s.isQuestFinished(rule.getQuest()));
        m.put(RuleType.SKILL_LEVEL, threshold((rule, s, v) -> s.skillLevel(rule.getSkill()) >= v));
        m.put(RuleType.SKILL_XP, threshold((rule, s, v) -> s.skillXp(rule.getSkill()) >= v));
        m.put(RuleType.TOTAL_LEVEL, threshold((rule, s, v) -> s.totalLevel() >= v));
        m.put(RuleType.BASE_LEVEL, threshold((rule, s, v) -> s.lowestSkillLevel() >= v));
        m.put(RuleType.ANY_SKILL_LEVEL, threshold((rule, s, v) -> s.highestSkillLevel() >= v));
        m.put(RuleType.COMBAT_LEVEL, threshold((rule, s, v) -> s.combatLevel() >= v));
        m.put(RuleType.DIARY_TIER, (rule, s) -> s.isDiaryTierComplete(rule.getArea(), rule.getTier()));
        m.put(RuleType.CA_POINTS, threshold((rule, s, v) -> s.hasCombatPoints(v)));
        m.put(RuleType.CA_COUNT, threshold((rule, s, v) -> s.completedCombatCount() >= v));
        m.put(RuleType.CA_TIER_COUNT, threshold((rule, s, v) -> s.completedCombatCountForTier(rule.getTier()) >= v));
        m.put(RuleType.CA_TIER_ALL, (rule, s) -> s.allIndexedCombatTierComplete(rule.getTier()));
        m.put(RuleType.CA_GROUP, (rule, s) -> s.allGroupPrefixesComplete(rule.getGroups()));
        m.put(RuleType.BOSS_KILL, threshold((rule, s, v) -> s.bossKillMeetsThreshold(rule.getVarp(), v)));
        m.put(RuleType.BOSS_KILL_ALL, threshold((rule, s, v) -> s.allBossKillsMeetThreshold(rule.getGroups(), v)));
        m.put(RuleType.BOSS_KILL_ANY, threshold((rule, s, v) -> s.anyBossKillMeetsThreshold(rule.getGroups(), v)));
        m.put(RuleType.BOSS_KILL_HISCORES, threshold((rule, s, v) -> s.hiscoresBossMeetsThreshold(rule.getBoss(), v)));
        m.put(RuleType.CLUE_COUNT, threshold((rule, s, v) -> s.clueTierMeetsThreshold(rule.getTier(), v)));
        EVALUATORS = Collections.unmodifiableMap(m);
    }

    private AutoDetectRuleEvaluators()
    {
    }

    /**
     * Whether game state satisfies one rule. An unrecognised type returns
     * false rather than throwing, so a data file newer than the plugin
     * degrades to "not detected" instead of breaking the pass.
     */
    static boolean isSatisfied(TaskEntry.AutoDetectRule rule, AutoDetectGameState state)
    {
        RuleEvaluator evaluator = rule.getRuleType() != null ? EVALUATORS.get(rule.getRuleType()) : null;
        return evaluator != null && state != null && evaluator.isSatisfied(rule, state);
    }

    static Set<RuleType> supportedTypes()
    {
        return EVALUATORS.keySet();
    }

    /** A rule without a {@code value} can't be compared, so it never matches. */
    private static RuleEvaluator threshold(Threshold check)
    {
        return (rule, state) -> rule.getValue() != null && check.meets(rule, state, rule.getValue());
    }
}
