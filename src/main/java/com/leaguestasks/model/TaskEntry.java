package com.leaguestasks.model;

import java.util.List;
import lombok.Getter;

/**
 * One row from leagues_tasks.json. Deserialized by Gson, which populates
 * fields by name via reflection, so no constructor or setters are needed.
 *
 * <p>{@code id} is a content-derived slug of {@code name} (lowercase,
 * hyphenated, max 48 chars) -- stable across scrapes as long as the task
 * name does not change. {@code points} is null when no league published a
 * value.
 */
@Getter
public class TaskEntry
{
    private String id;
    private String region;
    private String difficulty;
    private Integer points;
    private String name;
    private String description;
    private String requirements;
    /**
     * Structured breakdown of {@code requirements}, populated only for
     * quest-completion tasks. Null/empty for every other task, which fall
     * back to the flat {@code requirements} string.
     */
    private List<RequirementItem> requirementItems;
    private String activityType;
    /**
     * How the plugin can tell this task is done by reading game state, or
     * null for the majority of tasks that nothing in the client can answer.
     */
    private AutoDetectRule autoDetect;

    /**
     * A machine-checkable statement of what completing this task requires,
     * derived from the task text at scrape time so that tuning the rules
     * is a data change rather than a code change.
     *
     * <p>The rule kind is {@code type}; see {@link RuleType} for the full
     * set and which of {@code skill}/{@code quest}/{@code value}/{@code area}/
     * {@code tier}/{@code groups}/{@code varp}/{@code boss} each one reads.
     * An unrecognised {@code type} is ignored rather than treated as an
     * error, so a newer data file can add rule kinds without breaking an
     * older plugin.
     *
     * <p>{@code quest} is the wiki's quest <em>name</em>, not a numeric id:
     * the scraper has no access to RuneLite's {@code Quest} enum, so the
     * plugin resolves the name to an enum constant at runtime.
     */
    @Getter
    public static class AutoDetectRule
    {
        private String type;
        private String skill;
        private String quest;
        private Long value;
        private String area;
        private String tier;
        private List<String> groups;
        private String varp;
        private String boss;

        /** The parsed {@link #getType()}, or null when the string is unrecognised. */
        public RuleType getRuleType()
        {
            return RuleType.from(type);
        }
    }

    /**
     * One line of a structured requirements list: its own text (e.g.
     * "20 Thieving"), plus zero or more sub-items to render indented
     * underneath it (nested prerequisite quests, or wiki annotations
     * such as "not boostable").
     */
    @Getter
    public static class RequirementItem
    {
        private String text;
        private List<String> subItems;
    }
}
