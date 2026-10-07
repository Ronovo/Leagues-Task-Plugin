package com.leaguestasks.sync;

import lombok.Value;

/**
 * One combat achievement task sync snapshot -- the task slug comes from the
 * {@code CA_TASK_*_COMPLETED} varbit name. Gson-serialized only.
 */
@Value
public class CombatAchievementTask
{
    String task;
    boolean complete;
}
