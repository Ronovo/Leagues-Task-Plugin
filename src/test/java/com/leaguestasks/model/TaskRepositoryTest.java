package com.leaguestasks.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskRepositoryTest
{
    private final TaskRepository repository = new TaskRepository();

    @Test
    public void anyMatchesEveryDimension()
    {
        List<TaskEntry> all = repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY);
        List<TaskEntry> kourend = repository.allMatching("Kourend", TaskRepository.ANY, TaskRepository.ANY);
        assertFalse(all.isEmpty());
        assertFalse(kourend.isEmpty());
        assertTrue(all.size() > kourend.size());
    }

    @Test
    public void matchingIsCaseInsensitive()
    {
        List<TaskEntry> mixed = repository.allMatching("kourend", "HARD", "combat");
        List<TaskEntry> canonical = repository.allMatching("Kourend", "Hard", "Combat");
        assertEquals(canonical.size(), mixed.size());
    }

    @Test
    public void difficultyTiersAreInLogicalOrder()
    {
        assertEquals(
            Arrays.asList("Beginner", "Easy", "Medium", "Hard", "Elite", "Master"),
            repository.difficultyTiersInOrder());
    }

    @Test
    public void eligibleListsStartWithAnyAndAreReachable()
    {
        List<String> regions = repository.eligibleRegions(TaskRepository.ANY, TaskRepository.ANY);
        List<String> difficulties = repository.eligibleDifficulties(TaskRepository.ANY, TaskRepository.ANY);
        List<String> types = repository.eligibleActivityTypes(TaskRepository.ANY, TaskRepository.ANY);

        assertEquals(TaskRepository.ANY, regions.get(0));
        assertEquals(TaskRepository.ANY, difficulties.get(0));
        assertEquals(TaskRepository.ANY, types.get(0));

        for (String region : regions)
        {
            if (TaskRepository.ANY.equals(region))
            {
                continue;
            }
            assertFalse(repository.allMatching(region, TaskRepository.ANY, TaskRepository.ANY).isEmpty());
        }
    }

    @Test
    public void randomTaskEmptyWhenNoMatch()
    {
        Optional<TaskEntry> miss = repository.randomTask("Kourend", "Beginner", "Clue Scrolls");
        if (miss.isPresent())
        {
            // Combination happens to exist in the dataset; an invented region never will.
            miss = repository.randomTask("Not A Region", TaskRepository.ANY, TaskRepository.ANY);
        }
        assertFalse(miss.isPresent());
    }

    @Test
    public void findByIdHitAndMiss()
    {
        TaskEntry sample = repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY).get(0);
        assertTrue(repository.findById(sample.getId()).isPresent());
        assertFalse(repository.findById("definitely-not-a-task").isPresent());
    }

    @Test
    public void randomTaskExcludesCompletedIds()
    {
        List<TaskEntry> matches = repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY);
        Set<String> excludeAll = new HashSet<>();
        for (TaskEntry t : matches)
        {
            excludeAll.add(t.getId());
        }

        assertFalse(repository.randomTask(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY, excludeAll).isPresent());

        Set<String> excludeOne = new HashSet<>(Collections.singleton(matches.get(0).getId()));
        for (int i = 0; i < 20; i++)
        {
            Optional<TaskEntry> pick = repository.randomTask(
                TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY, excludeOne);
            assertTrue(pick.isPresent());
            assertFalse(excludeOne.contains(pick.get().getId()));
        }
    }

    @Test
    public void eligibleDifficultiesOmitsFullyCompletedTiers()
    {
        List<TaskEntry> easyTasks = repository.allMatching(TaskRepository.ANY, "Easy", TaskRepository.ANY);
        assertFalse(easyTasks.isEmpty());

        Set<String> excludeAllEasy = new HashSet<>();
        for (TaskEntry t : easyTasks)
        {
            excludeAllEasy.add(t.getId());
        }

        List<String> eligible = repository.eligibleDifficulties(
            TaskRepository.ANY, TaskRepository.ANY, excludeAllEasy);
        assertEquals(TaskRepository.ANY, eligible.get(0));
        assertFalse(eligible.contains("Easy"));
    }

    @Test
    public void preferLowerIncompleteKillDowngradesOneTierAtATime()
    {
        TaskEntry rolled = requireTask("1000-wintertodt-kills");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("500-wintertodt-kills", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillDowngrades10To1WhenFirstKillIncomplete()
    {
        TaskEntry rolled = requireTask("10-wintertodt-kills");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("1-wintertodt-kill", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillDowngrades25To10WhenLowerTiersIncomplete()
    {
        TaskEntry rolled = requireTask("25-wintertodt-kills");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("10-wintertodt-kills", served.getId());

        Set<String> completed = new HashSet<>(Arrays.asList("10-wintertodt-kills", "1-wintertodt-kill"));
        served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("25-wintertodt-kills", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillKeepsRolledWhenLowerTiersCompleted()
    {
        TaskEntry rolled = requireTask("25-wintertodt-kills");
        Set<String> completed = new HashSet<>(Arrays.asList("1-wintertodt-kill", "10-wintertodt-kills"));
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("25-wintertodt-kills", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillServesNextLowerIncompleteTier()
    {
        TaskEntry rolled = requireTask("50-wintertodt-kills");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("25-wintertodt-kills", served.getId());

        Set<String> completed = new HashSet<>(Collections.singleton("1-wintertodt-kill"));
        served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("25-wintertodt-kills", served.getId());

        completed.add("25-wintertodt-kills");
        served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("10-wintertodt-kills", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillLeavesNonKillTasksUnchanged()
    {
        TaskEntry rolled = requireTask("loot-a-barrows-chest");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("loot-a-barrows-chest", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillWorksForHiscoresBossFamily()
    {
        TaskEntry rolled = requireTask("25-skotizo-kills");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("10-skotizo-kills", served.getId());
    }

    @Test
    public void preferLowerIncompleteClueCountDowngradesWithinTheSameTier()
    {
        TaskEntry served = repository.preferLowerIncompleteKill(
            requireTask("50-hard-clue-scrolls"), Collections.emptySet());
        assertEquals("25-hard-clue-scrolls", served.getId());
    }

    @Test
    public void preferLowerIncompleteClueCountDowngradesToFirstCompletion()
    {
        TaskEntry served = repository.preferLowerIncompleteKill(
            requireTask("10-elite-clue-scrolls"), Collections.emptySet());
        assertEquals("1-elite-clue-scroll", served.getId());
    }

    @Test
    public void preferLowerIncompleteClueCountNeverCrossesTiers()
    {
        // Every lower HARD threshold done -> keep the rolled task rather than
        // fall through to a lower ELITE one.
        Set<String> completed = new HashSet<>(Arrays.asList(
            "1-hard-clue-scroll", "10-hard-clue-scrolls", "25-hard-clue-scrolls"));
        TaskEntry served = repository.preferLowerIncompleteKill(
            requireTask("50-hard-clue-scrolls"), completed);
        assertEquals("50-hard-clue-scrolls", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillDowngradesGiantMole50ToFirstKill()
    {
        TaskEntry rolled = requireTask("defeat-the-giant-mole-50-times");
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("defeat-the-giant-mole", served.getId());
        assertEquals("Defeat the Giant Mole", served.getName());
    }

    @Test
    public void preferLowerIncompleteKillDowngradesDefeatNex25To10()
    {
        TaskEntry rolled = requireTask("defeat-nex-25-times");
        assertEquals("Defeat Nex 25 Times", rolled.getName());

        TaskEntry served = repository.preferLowerIncompleteKill(rolled, Collections.emptySet());
        assertEquals("defeat-nex-10-times", served.getId());
        assertEquals("Defeat Nex 10 Times", served.getName());
    }

    @Test
    public void preferLowerIncompleteKillKeepsDefeatNex25WhenLowerTiersCompleted()
    {
        TaskEntry rolled = requireTask("defeat-nex-25-times");
        Set<String> completed = new HashSet<>(Arrays.asList(
            "defeat-nex", "defeat-nex-10-times"));
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("defeat-nex-25-times", served.getId());
    }

    @Test
    public void preferLowerIncompleteKillDowngradesDefeatNex25ToFirstKillWhen10Done()
    {
        TaskEntry rolled = requireTask("defeat-nex-25-times");
        Set<String> completed = new HashSet<>(Collections.singleton("defeat-nex-10-times"));
        TaskEntry served = repository.preferLowerIncompleteKill(rolled, completed);
        assertEquals("defeat-nex", served.getId());
    }

    @Test
    public void activityTypesIncludeSplitBossAndMinigame()
    {
        List<String> types = repository.eligibleActivityTypes(TaskRepository.ANY, TaskRepository.ANY);
        assertTrue(types.contains("Boss"));
        assertTrue(types.contains("Minigame"));
        assertFalse(types.contains("Boss/Minigame"));
        assertFalse(types.contains("Minigame/Boss"));
    }

    @Test
    public void noLegacyBossMinigameActivityLabels()
    {
        for (TaskEntry task : repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY))
        {
            String activityType = task.getActivityType();
            assertFalse("legacy combined label on " + task.getId(),
                "Boss/Minigame".equalsIgnoreCase(activityType)
                    || "Minigame/Boss".equalsIgnoreCase(activityType));
        }
    }

    @Test
    public void wintertodtKillTasksAreKourendMinigame()
    {
        for (TaskEntry task : repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY))
        {
            if (!task.getName().toLowerCase().contains("wintertodt")
                || !task.getName().toLowerCase().contains("kill"))
            {
                continue;
            }
            assertEquals("Kourend", task.getRegion());
            assertEquals("Minigame", task.getActivityType());
        }
    }

    @Test
    public void skotizoAndCoxTasksAreKourendBoss()
    {
        TaskEntry skotizo = requireTask("1-skotizo-kill");
        assertEquals("Kourend", skotizo.getRegion());
        assertEquals("Boss", skotizo.getActivityType());

        TaskEntry cox = requireTask("1-chambers-of-xeric");
        assertEquals("Kourend", cox.getRegion());
        assertEquals("Boss", cox.getActivityType());
    }

    @Test
    public void wintertodtKillTasksAreSortedNumericallyAndContiguous()
    {
        List<TaskEntry> minigames = repository.allMatching("Kourend", TaskRepository.ANY, "Minigame");
        List<TaskEntry> wintertodtKills = new ArrayList<>();
        for (TaskEntry task : minigames)
        {
            if (task.getName().toLowerCase().contains("wintertodt")
                && task.getName().toLowerCase().contains("kill"))
            {
                wintertodtKills.add(task);
            }
        }

        assertFalse(wintertodtKills.isEmpty());
        assertKillFamilySortedAndContiguous(minigames, wintertodtKills);
        assertEquals(1L, BossKillTasks.killThreshold(wintertodtKills.get(0)));
        assertEquals(1000L, BossKillTasks.killThreshold(wintertodtKills.get(wintertodtKills.size() - 1)));
    }

    @Test
    public void skotizoKillTasksAreSortedNumericallyAndContiguous()
    {
        List<TaskEntry> bosses = repository.allMatching("Kourend", TaskRepository.ANY, "Boss");
        List<TaskEntry> skotizoKills = new ArrayList<>();
        for (TaskEntry task : bosses)
        {
            if (task.getName().toLowerCase().contains("skotizo")
                && task.getName().toLowerCase().contains("kill"))
            {
                skotizoKills.add(task);
            }
        }

        assertFalse(skotizoKills.isEmpty());
        assertKillFamilySortedAndContiguous(bosses, skotizoKills);
    }

    @Test
    public void allBossKillFamiliesAreNonDecreasingInFullList()
    {
        List<TaskEntry> all = repository.allMatching(
            TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY);

        String currentFamilyKey = null;
        Long previousValue = null;

        for (TaskEntry task : all)
        {
            long value = BossKillTasks.killThreshold(task);
            if (value < 0)
            {
                currentFamilyKey = null;
                previousValue = null;
                continue;
            }

            String familyKey = BossKillTasks.bossKillFamilyKey(task);
            if (familyKey == null)
            {
                currentFamilyKey = null;
                previousValue = null;
                continue;
            }

            if (familyKey.equals(currentFamilyKey))
            {
                assertTrue("boss kill family " + familyKey + " out of order at " + task.getId(),
                    previousValue == null || value >= previousValue);
            }
            else
            {
                currentFamilyKey = familyKey;
            }
            previousValue = value;
        }
    }

    private static void assertKillFamilySortedAndContiguous(
        List<TaskEntry> filteredList, List<TaskEntry> familyTasks)
    {
        long previousValue = -1;
        for (TaskEntry task : familyTasks)
        {
            long value = BossKillTasks.killThreshold(task);
            assertTrue("expected increasing kill counts in " + task.getId(), value > previousValue);
            previousValue = value;
        }

        int firstIndex = filteredList.indexOf(familyTasks.get(0));
        int lastIndex = filteredList.indexOf(familyTasks.get(familyTasks.size() - 1));
        assertEquals("kill family should be one contiguous block",
            familyTasks.size() - 1, lastIndex - firstIndex);
    }

    @Test
    public void nexKillTasksAreSortedNumericallyAndContiguous()
    {
        List<TaskEntry> bosses = repository.allMatching(TaskRepository.ANY, TaskRepository.ANY, "Boss");
        List<TaskEntry> nexKills = new ArrayList<>();
        for (TaskEntry task : bosses)
        {
            if (task.getName().toLowerCase().contains("defeat nex")
                && BossKillTasks.isKillCountName(task.getName()))
            {
                nexKills.add(task);
            }
        }

        assertFalse(nexKills.isEmpty());
        assertKillFamilySortedAndContiguous(bosses, nexKills);
        assertEquals(10L, BossKillTasks.killThreshold(nexKills.get(0)));
    }

    @Test
    public void pestControlGameTasksAreMinigame()
    {
        TaskEntry novice = requireTask("complete-a-game-of-novice-pest-control");
        assertEquals("Minigame", novice.getActivityType());
    }

    private TaskEntry requireTask(String id)
    {
        return repository.findById(id).orElseThrow(() -> new AssertionError("missing task " + id));
    }
}
