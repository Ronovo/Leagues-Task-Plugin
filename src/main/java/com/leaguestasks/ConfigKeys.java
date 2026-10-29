package com.leaguestasks;

/**
 * Every key this plugin persists, in one place.
 *
 * <p>Everything shares the one {@link #GROUP} -- RuneLite shows a plugin's
 * settings by config group. Saved lists and completions use a
 * {@code leagues.} prefix. Ronovo's Sync Tool reads the completions keys, so
 * their names must not change.
 */
public final class ConfigKeys
{
    public static final String GROUP = "leaguestasks";

    private static final String LEAGUES = "leagues.";

    public static final String SAVED_TASKS = LEAGUES + "savedTasks";
    public static final String SAVED_TASKS_PREFIX = SAVED_TASKS + ".";

    /**
     * Per-account completions: this prefix plus the normalized RS display
     * name. Keeping each account's list apart matters now that completions
     * can be detected automatically from game state -- an alt's levels
     * must not land in the main's list.
     */
    public static final String COMPLETED_TASKS = LEAGUES + "completedTasks";
    public static final String COMPLETED_TASKS_PREFIX = COMPLETED_TASKS + ".";

    /** Last account seen, so the panel shows a sensible list when logged out. */
    public static final String LAST_ACCOUNT = LEAGUES + "lastAccount";

    private ConfigKeys()
    {
    }
}
