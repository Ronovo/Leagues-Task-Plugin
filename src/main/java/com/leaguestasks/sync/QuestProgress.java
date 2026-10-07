package com.leaguestasks.sync;

import lombok.Value;

/**
 * One quest's sync snapshot -- RuneLite's own stable numeric id (from the
 * {@code Quest} enum, independent of quest name so a rename can't break
 * matching on the website side), display name, and a
 * {@code QuestState} name ({@code NOT_STARTED}/{@code IN_PROGRESS}/
 * {@code FINISHED}). Gson-serialized only: written into a sync payload, never read back.
 */
@Value
public class QuestProgress
{
    int id;
    String name;
    String state;
}
