package com.leaguestasks;

import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DataIntegrityTest
{
    /** Update when leagues_tasks.json gains or loses tasks. */
    private static final int EXPECTED_TASK_COUNT = 2269;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Set<String> DIFFICULTIES = new HashSet<>(Arrays.asList(
        "beginner", "easy", "medium", "hard", "elite", "master"));
    /**
     * Per-region task counts; must sum to {@link #EXPECTED_TASK_COUNT}. A
     * failing count names the region that moved, which narrows down which
     * rows of leagues_tasks.json changed.
     */
    private static final Map<String, Integer> EXPECTED_COUNT_BY_REGION = new HashMap<>();

    static
    {
        EXPECTED_COUNT_BY_REGION.put("Asgarnia", 121);
        EXPECTED_COUNT_BY_REGION.put("Fremennik Provinces", 130);
        EXPECTED_COUNT_BY_REGION.put("General", 816);
        EXPECTED_COUNT_BY_REGION.put("Kandarin", 120);
        EXPECTED_COUNT_BY_REGION.put("Karamja", 74);
        EXPECTED_COUNT_BY_REGION.put("Kharidian Desert", 157);
        EXPECTED_COUNT_BY_REGION.put("Kourend", 260);
        EXPECTED_COUNT_BY_REGION.put("Misthalin", 140);
        EXPECTED_COUNT_BY_REGION.put("Morytania", 136);
        EXPECTED_COUNT_BY_REGION.put("Tirannwn", 108);
        EXPECTED_COUNT_BY_REGION.put("Varlamore", 105);
        EXPECTED_COUNT_BY_REGION.put("Wilderness", 102);
    }

    private final TaskRepository repository = new TaskRepository();

    @Test
    public void expectedTaskCount()
    {
        Map<String, Integer> actual = new HashMap<>();
        for (TaskEntry t : allTasks())
        {
            actual.merge(t.getRegion(), 1, Integer::sum);
        }

        assertEquals("task count per region changed (expected -> actual):"
            + CountDiff.describe(EXPECTED_COUNT_BY_REGION, actual), EXPECTED_COUNT_BY_REGION, actual);
        assertEquals(EXPECTED_TASK_COUNT, allTasks().size());
    }

    @Test
    public void regionCountsAddUpToTheTotal()
    {
        int sum = EXPECTED_COUNT_BY_REGION.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(EXPECTED_TASK_COUNT, sum);
    }

    @Test
    public void idsAreUniqueNonEmptySlugs()
    {
        List<TaskEntry> tasks = allTasks();
        Set<String> ids = new HashSet<>();
        for (TaskEntry t : tasks)
        {
            assertNotNull(t.getId());
            assertFalse(t.getId().isEmpty());
            assertTrue(t.getId(), SLUG.matcher(t.getId()).matches());
            assertTrue("duplicate id " + t.getId(), ids.add(t.getId()));
        }
        assertEquals(tasks.size(), ids.size());
    }

    @Test
    public void namesAreUniqueWhenNormalized()
    {
        Set<String> keys = new HashSet<>();
        for (TaskEntry t : allTasks())
        {
            assertNotNull(t.getName());
            assertFalse(t.getName().isEmpty());
            String key = t.getName().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", " ").trim();
            assertTrue("duplicate normalized name: " + t.getName(), keys.add(key));
        }
    }

    @Test
    public void difficultiesAndRegionsAreKnown()
    {
        for (TaskEntry t : allTasks())
        {
            assertTrue(t.getDifficulty(), DIFFICULTIES.contains(t.getDifficulty()));
            assertTrue(t.getRegion(), EXPECTED_COUNT_BY_REGION.containsKey(t.getRegion()));
        }
    }

    private List<TaskEntry> allTasks()
    {
        return repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY);
    }
}
