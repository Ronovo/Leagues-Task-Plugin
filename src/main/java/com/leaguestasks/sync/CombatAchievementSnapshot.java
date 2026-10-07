package com.leaguestasks.sync;

import java.util.List;
import lombok.Value;

/**
 * Combat achievement progress at one point in time: total CA points plus every
 * individual task varbit the catalog knows about. Gson-serialized only.
 */
@Value
public class CombatAchievementSnapshot
{
    int points;
    List<CombatAchievementTask> tasks;
}
