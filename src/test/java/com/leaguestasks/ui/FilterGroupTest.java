package com.leaguestasks.ui;

import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FilterGroupTest
{
    private static final String ANY = TaskRepository.ANY;

    private final TaskRepository repository = new TaskRepository();

    @Test
    public void refreshOffersEveryEligibleOptionWithNothingSelected()
    {
        FilterGroup filters = new FilterGroup(repository, null, null);
        filters.refresh();

        assertEquals(repository.eligibleRegions(ANY, ANY), items(filters.region));
        assertEquals(repository.eligibleDifficulties(ANY, ANY), items(filters.difficulty));
        assertEquals(repository.eligibleActivityTypes(ANY, ANY), items(filters.type));
    }

    @Test
    public void choosingARegionNarrowsTheOtherDropdowns()
    {
        FilterGroup filters = new FilterGroup(repository, null, null);
        filters.refresh();
        String region = repository.eligibleRegions(ANY, ANY).get(1);

        filters.region.setSelectedItem(region);

        assertEquals(region, filters.region());
        assertEquals(repository.eligibleDifficulties(region, ANY), items(filters.difficulty));
        assertEquals(repository.eligibleActivityTypes(region, ANY), items(filters.type));
    }

    @Test
    public void aSelectionWhoseTasksAreAllCompletedFallsBackToAny()
    {
        Set<String> completed = new HashSet<>();
        FilterGroup filters = new FilterGroup(repository, () -> completed, null);
        filters.refresh();
        String region = repository.eligibleRegions(ANY, ANY).get(1);
        filters.region.setSelectedItem(region);

        for (TaskEntry task : repository.allMatching(region, ANY, ANY))
        {
            completed.add(task.getId());
        }
        filters.refresh();

        assertEquals(ANY, filters.region());
    }

    @Test
    public void excludingEveryTaskLeavesOnlyAny()
    {
        Set<String> all = repository.allMatching(ANY, ANY, ANY).stream()
            .map(TaskEntry::getId)
            .collect(Collectors.toSet());
        FilterGroup filters = new FilterGroup(repository, () -> all, null);
        filters.refresh();

        assertEquals(Collections.singletonList(ANY), items(filters.region));
        assertEquals(Collections.singletonList(ANY), items(filters.difficulty));
        assertEquals(Collections.singletonList(ANY), items(filters.type));
    }

    @Test
    public void userChangesNotifyOnceAndSetFiltersStaysQuiet()
    {
        AtomicInteger changes = new AtomicInteger();
        FilterGroup filters = new FilterGroup(repository, null, changes::incrementAndGet);
        filters.refresh();
        changes.set(0);

        filters.setFilters(ANY, "Easy", ANY);
        assertEquals(0, changes.get());

        filters.type.setSelectedItem(repository.eligibleActivityTypes(ANY, "Easy").get(1));
        assertEquals(1, changes.get());
    }

    private static List<String> items(JComboBox<String> combo)
    {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++)
        {
            out.add(combo.getItemAt(i));
        }
        return out;
    }
}
