package com.leaguestasks.model;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Threshold-count task helpers: shared family grouping and threshold parsing
 * for the "do X N times" tasks that {@link TaskRepository} clusters and the
 * picker downgrades. Covers boss/minigame kill counts (whose names come in
 * two shapes -- {@code "25 Wintertodt Kills"} and {@code "Defeat Nex 25
 * Times"}) and per-tier clue scroll counts (which carry their threshold in
 * the rule's {@code value}, not the name).
 */
final class BossKillTasks
{
    private static final Pattern KILL_COUNT_FRONT = Pattern.compile(
        "^(\\d[\\d,]*)\\s+(.+?)\\s+Kills?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFEAT_TIMES = Pattern.compile(
        "^(?:Defeat|Complete|Subdue)\\s+(?:the\\s+)?(.+?)\\s+(\\d[\\d,]*)\\s+Times?$",
        Pattern.CASE_INSENSITIVE);

    private BossKillTasks()
    {
    }

    static long killThreshold(TaskEntry task)
    {
        if (task == null)
        {
            return -1;
        }

        TaskEntry.AutoDetectRule rule = task.getAutoDetect();
        if (rule != null && rule.getValue() != null)
        {
            return rule.getValue();
        }

        return killThresholdFromName(task.getName()).orElse(-1L);
    }

    static OptionalLong killThresholdFromName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return OptionalLong.empty();
        }

        String trimmed = name.trim();
        Matcher front = KILL_COUNT_FRONT.matcher(trimmed);
        if (front.matches())
        {
            return OptionalLong.of(parseCount(front.group(1)));
        }

        Matcher times = DEFEAT_TIMES.matcher(trimmed);
        if (times.matches())
        {
            return OptionalLong.of(parseCount(times.group(2)));
        }

        return OptionalLong.empty();
    }

    static boolean isKillCountName(String name)
    {
        return killThresholdFromName(name).isPresent();
    }

    static String bossKillFamilyKey(TaskEntry task)
    {
        TaskEntry.AutoDetectRule rule = task.getAutoDetect();
        RuleType type = rule == null ? null : rule.getRuleType();
        if (type == null)
        {
            return null;
        }

        switch (type)
        {
            case BOSS_KILL:
                return rule.getVarp() != null ? type.name() + "|" + rule.getVarp() : null;
            case BOSS_KILL_HISCORES:
                return rule.getBoss() != null ? type.name() + "|" + rule.getBoss() : null;
            case BOSS_KILL_ALL:
            case BOSS_KILL_ANY:
                if (rule.getGroups() == null || rule.getGroups().isEmpty())
                {
                    return null;
                }
                return type.name() + "|" + String.join(",", rule.getGroups());
            case CLUE_COUNT:
                return rule.getTier() != null ? type.name() + "|" + rule.getTier() : null;
            default:
                return null;
        }
    }

    private static long parseCount(String raw)
    {
        return Long.parseLong(raw.replace(",", ""));
    }
}
