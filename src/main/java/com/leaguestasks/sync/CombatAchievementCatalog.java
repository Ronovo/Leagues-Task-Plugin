package com.leaguestasks.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads combat achievement task completion and total points from named
 * {@code VarbitID} fields. Every {@link #build} call must run on the client
 * thread.
 */
@Singleton
public class CombatAchievementCatalog
{
    private static final Logger log = LoggerFactory.getLogger(CombatAchievementCatalog.class);
    private static final Pattern TASK_COMPLETED = Pattern.compile("^CA_TASK_(.+)_COMPLETED$");
    private static final String POINTS_FIELD = "CA_POINTS";

    private final List<TaskVarbit> taskVarbits;
    private final int pointsVarbitId;

    @Inject
    public CombatAchievementCatalog()
    {
        taskVarbits = discoverTaskVarbits();
        pointsVarbitId = VarbitIdFields.resolveStaticIntField(POINTS_FIELD, log);
        if (taskVarbits.isEmpty() || pointsVarbitId < 0)
        {
            log.warn("CombatAchievementCatalog matched zero task varbits or missing CA_POINTS -- CA sync will be skipped");
        }
    }

    /** False when no task varbits matched or {@code CA_POINTS} is missing. */
    public boolean isAvailable()
    {
        return !taskVarbits.isEmpty() && pointsVarbitId >= 0;
    }

    public CombatAchievementSnapshot build(Client client)
    {
        if (!isAvailable())
        {
            return null;
        }

        List<CombatAchievementTask> tasks = new ArrayList<>(taskVarbits.size());
        for (TaskVarbit entry : taskVarbits)
        {
            boolean complete = client.getVarbitValue(entry.varbitId) != 0;
            tasks.add(new CombatAchievementTask(entry.task, complete));
        }
        int points = client.getVarbitValue(pointsVarbitId);
        return new CombatAchievementSnapshot(points, tasks);
    }

    /** Every task slug this catalog discovered from {@code VarbitID}. */
    public List<String> discoveredSlugs()
    {
        List<String> slugs = new ArrayList<>(taskVarbits.size());
        for (TaskVarbit entry : taskVarbits)
        {
            slugs.add(entry.task);
        }
        return slugs;
    }

    /** True when every discovered slug whose name starts with {@code prefix + "_"} is complete. */
    public static boolean allPrefixesComplete(CombatAchievementSnapshot snapshot, List<String> prefixes)
    {
        if (snapshot == null || prefixes == null || prefixes.isEmpty())
        {
            return false;
        }

        for (CombatAchievementTask task : snapshot.getTasks())
        {
            String slug = task.getTask();
            for (String prefix : prefixes)
            {
                if (slug.startsWith(prefix + "_") && !task.isComplete())
                {
                    return false;
                }
            }
        }

        for (String prefix : prefixes)
        {
            boolean found = false;
            for (CombatAchievementTask task : snapshot.getTasks())
            {
                if (task.getTask().startsWith(prefix + "_"))
                {
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                return false;
            }
        }
        return true;
    }

    private static List<TaskVarbit> discoverTaskVarbits()
    {
        return VarbitIdFields.discover(TASK_COMPLETED,
            (field, matcher) -> new TaskVarbit(matcher.group(1), field.getInt(null)),
            log);
    }

    private static final class TaskVarbit
    {
        final String task;
        final int varbitId;

        TaskVarbit(String task, int varbitId)
        {
            this.task = task;
            this.varbitId = varbitId;
        }
    }
}
