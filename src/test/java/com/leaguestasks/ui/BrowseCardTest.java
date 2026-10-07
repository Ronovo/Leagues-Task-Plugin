package com.leaguestasks.ui;

import com.google.gson.Gson;
import com.leaguestasks.model.TaskEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BrowseCardTest
{
    private final Gson gson = new Gson();

    private TaskEntry task(String id)
    {
        return gson.fromJson("{\"id\":\"" + id + "\"}", TaskEntry.class);
    }

    @Test
    public void filterByStatusAnyKeepsAllTasks()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"), task("c"));
        Set<String> completed = new HashSet<>(Collections.singletonList("b"));

        List<TaskEntry> filtered = BrowseCard.filterByStatus(tasks, BrowseCard.STATUS_ANY, completed);

        assertEquals(3, filtered.size());
        assertEquals("a", filtered.get(0).getId());
        assertEquals("b", filtered.get(1).getId());
        assertEquals("c", filtered.get(2).getId());
    }

    @Test
    public void filterByStatusIncompleteDropsCompleted()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"), task("c"));
        Set<String> completed = new HashSet<>(Arrays.asList("a", "c"));

        List<TaskEntry> filtered = BrowseCard.filterByStatus(tasks, BrowseCard.STATUS_INCOMPLETE, completed);

        assertEquals(1, filtered.size());
        assertEquals("b", filtered.get(0).getId());
    }

    @Test
    public void filterByStatusCompletedKeepsOnlyCompleted()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"), task("c"));
        Set<String> completed = new HashSet<>(Arrays.asList("a", "c"));

        List<TaskEntry> filtered = BrowseCard.filterByStatus(tasks, BrowseCard.STATUS_COMPLETED, completed);

        assertEquals(2, filtered.size());
        assertEquals("a", filtered.get(0).getId());
        assertEquals("c", filtered.get(1).getId());
    }

    @Test
    public void filterByStatusIncompleteWithNoCompletionsKeepsAll()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"));
        Set<String> completed = Collections.emptySet();

        List<TaskEntry> filtered = BrowseCard.filterByStatus(tasks, BrowseCard.STATUS_INCOMPLETE, completed);

        assertEquals(2, filtered.size());
    }

    @Test
    public void filterByStatusCompletedWithNoCompletionsIsEmpty()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"));
        Set<String> completed = Collections.emptySet();

        List<TaskEntry> filtered = BrowseCard.filterByStatus(tasks, BrowseCard.STATUS_COMPLETED, completed);

        assertTrue(filtered.isEmpty());
    }

    @Test
    public void formatCountLabelAnyShowsCompletedFraction()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("b"), task("c"));
        Set<String> completed = new HashSet<>(Collections.singletonList("b"));

        assertEquals("Completed: 1 / 3",
            BrowseCard.formatCountLabel(BrowseCard.STATUS_ANY, tasks, completed));
    }

    @Test
    public void formatCountLabelIncompleteShowsCount()
    {
        List<TaskEntry> tasks = Arrays.asList(task("b"));
        Set<String> completed = new HashSet<>(Arrays.asList("a", "c"));

        assertEquals("Incomplete: 1",
            BrowseCard.formatCountLabel(BrowseCard.STATUS_INCOMPLETE, tasks, completed));
    }

    @Test
    public void formatCountLabelCompletedShowsCount()
    {
        List<TaskEntry> tasks = Arrays.asList(task("a"), task("c"));
        Set<String> completed = new HashSet<>(Arrays.asList("a", "c"));

        assertEquals("Completed: 2",
            BrowseCard.formatCountLabel(BrowseCard.STATUS_COMPLETED, tasks, completed));
    }
}
