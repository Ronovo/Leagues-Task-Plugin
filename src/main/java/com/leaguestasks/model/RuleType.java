package com.leaguestasks.model;

/**
 * The kinds of auto-detect rule a {@link TaskEntry.AutoDetectRule} can carry,
 * and the single source of truth for their identifiers and the wording the
 * autocomplete breakdown shows.
 *
 * <p>Declaration order is the breakdown's display order. A rule whose
 * {@code type} string matches no constant here resolves to {@code null} via
 * {@link #from(String)}, and the autocomplete pass treats it as "not
 * detected" -- so a data file newer than the plugin degrades gracefully
 * rather than breaking.
 *
 * <p>Which of a rule's other fields matter depends on the type:
 * <ul>
 *   <li>{@link #QUEST} -- {@code quest}</li>
 *   <li>{@link #SKILL_LEVEL}, {@link #SKILL_XP} -- {@code skill} + {@code value}</li>
 *   <li>{@link #TOTAL_LEVEL}, {@link #BASE_LEVEL}, {@link #ANY_SKILL_LEVEL},
 *       {@link #COMBAT_LEVEL} -- {@code value}</li>
 *   <li>{@link #DIARY_TIER} -- {@code area} + {@code tier}</li>
 *   <li>{@link #CA_POINTS}, {@link #CA_COUNT} -- {@code value}</li>
 *   <li>{@link #CA_TIER_COUNT} -- {@code tier} + {@code value};
 *       {@link #CA_TIER_ALL} -- {@code tier}</li>
 *   <li>{@link #CA_GROUP} -- {@code groups}</li>
 *   <li>{@link #BOSS_KILL} -- {@code varp} + {@code value};
 *       {@link #BOSS_KILL_HISCORES} -- {@code boss} + {@code value};
 *       {@link #BOSS_KILL_ALL}, {@link #BOSS_KILL_ANY} -- {@code groups} + {@code value}</li>
 *   <li>{@link #CLUE_COUNT} -- {@code tier} + {@code value}</li>
 * </ul>
 */
public enum RuleType
{
    QUEST("Quests"),
    SKILL_LEVEL("Skill levels"),
    SKILL_XP("Skill XP"),
    TOTAL_LEVEL("Total level"),
    BASE_LEVEL("Base level"),
    ANY_SKILL_LEVEL("First level milestones"),
    COMBAT_LEVEL("Combat level"),
    DIARY_TIER("Achievement diaries"),
    CA_POINTS("Combat achievement points"),
    CA_COUNT("Combat achievements"),
    CA_TIER_COUNT("Combat achievements by tier"),
    CA_TIER_ALL("Full combat achievement tiers"),
    CA_GROUP("Boss combat achievements"),
    BOSS_KILL("Boss kills"),
    BOSS_KILL_ALL("Boss kills (each)"),
    BOSS_KILL_ANY("Boss kills (any)"),
    BOSS_KILL_HISCORES("Boss kills (hiscores)"),
    CLUE_COUNT("Clue scrolls");

    private final String label;

    RuleType(String label)
    {
        this.label = label;
    }

    /** The wording the autocomplete breakdown shows for this rule type. */
    public String label()
    {
        return label;
    }

    /**
     * @return the matching constant, or null for a null or unrecognised
     * string (the caller treats that as "not detected").
     */
    public static RuleType from(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            return valueOf(raw);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
