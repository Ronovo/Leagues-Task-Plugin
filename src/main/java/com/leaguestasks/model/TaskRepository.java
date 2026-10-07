package com.leaguestasks.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.leaguestasks.ui.TextUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Loads the unified leagues_tasks.json resource once, and serves
 * filtered/random lookups over it.
 */
@Singleton
public class TaskRepository
{
    public static final String ANY = "Any";

    private static final String RESOURCE_NAME = "/com/leaguestasks/leagues_tasks.json";

    // Logical difficulty progression, not alphabetical.
    static final List<String> DIFFICULTY_ORDER =
        Arrays.asList("beginner", "easy", "medium", "hard", "elite", "master");

    private final List<TaskEntry> tasks;
    private final Map<String, TaskEntry> byId;
    private final Map<String, List<String>> regionCache = new HashMap<>();
    private final Map<String, List<String>> difficultyCache = new HashMap<>();
    private final Map<String, List<String>> typeCache = new HashMap<>();
    private final Map<String, List<TaskEntry>> matchCache = new HashMap<>();
    /** Lazily built by {@link #autoDetectableTasks()}. */
    private List<TaskEntry> autoDetectable;
    /** Threshold-count tasks (boss kills, clue counts) grouped by shared auto-detect target. */
    private final Map<String, List<TaskEntry>> bossKillFamiliesByKey;
    private final Map<String, String> bossKillFamilyKeyByTaskId;

    public TaskRepository()
    {
        this(new Gson());
    }

    @Inject
    public TaskRepository(Gson gson)
    {
        List<TaskEntry> loaded = load(gson);

        Map<String, List<TaskEntry>> families = new HashMap<>();
        Map<String, String> familyKeyByTaskId = new HashMap<>();
        for (TaskEntry task : loaded)
        {
            String familyKey = BossKillTasks.bossKillFamilyKey(task);
            if (familyKey != null && BossKillTasks.killThreshold(task) >= 0)
            {
                families.computeIfAbsent(familyKey, k -> new ArrayList<>()).add(task);
                familyKeyByTaskId.put(task.getId(), familyKey);
            }
        }
        for (List<TaskEntry> family : families.values())
        {
            family.sort(Comparator.comparingLong(BossKillTasks::killThreshold));
        }

        this.tasks = clusterBossKillFamilies(loaded, families, familyKeyByTaskId);

        Map<String, TaskEntry> index = new HashMap<>();
        for (TaskEntry task : tasks)
        {
            index.put(task.getId(), task);
        }
        this.byId = index;

        Map<String, List<TaskEntry>> immutableFamilies = new HashMap<>();
        for (Map.Entry<String, List<TaskEntry>> entry : families.entrySet())
        {
            immutableFamilies.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        this.bossKillFamiliesByKey = Collections.unmodifiableMap(immutableFamilies);
        this.bossKillFamilyKeyByTaskId = Collections.unmodifiableMap(familyKeyByTaskId);
    }

    private List<TaskEntry> load(Gson gson)
    {
        try (InputStream is = getClass().getResourceAsStream(RESOURCE_NAME))
        {
            if (is == null)
            {
                throw new IllegalStateException(RESOURCE_NAME + " not found on classpath");
            }

            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
            {
                Type listType = new TypeToken<List<TaskEntry>>()
                {
                }.getType();
                List<TaskEntry> loaded = gson.fromJson(reader, listType);
                if (loaded == null)
                {
                    throw new IllegalStateException(RESOURCE_NAME + " deserialized to null");
                }
                return loaded;
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to load " + RESOURCE_NAME, e);
        }
    }

    public Optional<TaskEntry> findById(String id)
    {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Only the tasks carrying an auto-detect rule -- a few hundred of the
     * couple of thousand. Computed once and cached because
     * {@code TaskAutoDetector} re-sweeps this list on every preview and
     * apply, and walking the full task list each time to skip 80% of it
     * would be pure waste.
     */
    public List<TaskEntry> autoDetectableTasks()
    {
        if (autoDetectable == null)
        {
            autoDetectable = Collections.unmodifiableList(tasks.stream()
                .filter(t -> t.getAutoDetect() != null)
                .collect(Collectors.toList()));
        }
        return autoDetectable;
    }

    /**
     * The six real difficulty tiers (capitalized), in logical order,
     * excluding "Any" — used by the saved-task list view.
     */
    public List<String> difficultyTiersInOrder()
    {
        List<String> result = new ArrayList<>(DIFFICULTY_ORDER.size());
        for (String tier : DIFFICULTY_ORDER)
        {
            result.add(TextUtil.capitalize(tier));
        }
        return result;
    }

    /**
     * Regions with at least one task matching the given difficulty/activity
     * type filters ("Any" for either skips that constraint). Used so the
     * panel can keep every dropdown restricted to combinations that
     * actually have eligible tasks.
     */
    public List<String> eligibleRegions(String difficulty, String activityType)
    {
        return eligibleRegions(difficulty, activityType, Collections.emptySet());
    }

    public List<String> eligibleRegions(String difficulty, String activityType, Set<String> excludeIds)
    {
        return eligible(regionCache, cacheKey(difficulty, activityType), excludeIds,
            t -> matchesDifficulty(t, difficulty) && matchesActivityType(t, activityType),
            s -> distinctSorted(s, TaskEntry::getRegion));
    }

    /**
     * Difficulties with at least one task matching the given region/activity
     * type filters ("Any" for either skips that constraint). "Any" is always
     * first; remaining tiers follow {@link #DIFFICULTY_ORDER}.
     */
    public List<String> eligibleDifficulties(String region, String activityType)
    {
        return eligibleDifficulties(region, activityType, Collections.emptySet());
    }

    public List<String> eligibleDifficulties(String region, String activityType, Set<String> excludeIds)
    {
        return eligible(difficultyCache, cacheKey(region, activityType), excludeIds,
            t -> matchesRegion(t, region) && matchesActivityType(t, activityType),
            TaskRepository::orderedTiers);
    }

    /** "Any" followed by the tiers present in {@code stream}, in {@link #DIFFICULTY_ORDER}. */
    private static List<String> orderedTiers(Stream<TaskEntry> stream)
    {
        Set<String> present = stream
            .map(TaskEntry::getDifficulty)
            .filter(v -> v != null && !v.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toCollection(TreeSet::new));

        List<String> result = new ArrayList<>();
        result.add(ANY);
        for (String tier : DIFFICULTY_ORDER)
        {
            if (present.contains(tier))
            {
                result.add(TextUtil.capitalize(tier));
            }
        }
        return result;
    }

    /**
     * Activity types with at least one task matching the given
     * region/difficulty filters ("Any" for either skips that constraint).
     */
    public List<String> eligibleActivityTypes(String region, String difficulty)
    {
        return eligibleActivityTypes(region, difficulty, Collections.emptySet());
    }

    public List<String> eligibleActivityTypes(String region, String difficulty, Set<String> excludeIds)
    {
        return eligible(typeCache, cacheKey(region, difficulty), excludeIds,
            t -> matchesRegion(t, region) && matchesDifficulty(t, difficulty),
            s -> distinctSorted(s, TaskEntry::getActivityType));
    }

    /**
     * Shared body of the three {@code eligible*} lookups: summarise the tasks
     * passing {@code filter}. The unexcluded answer depends only on the
     * filter values, so it is cached under {@code key}; with exclusions it is
     * recomputed, since the excluded set changes as tasks are completed.
     */
    private List<String> eligible(Map<String, List<String>> cache, String key, Set<String> excludeIds,
        Predicate<TaskEntry> filter, Function<Stream<TaskEntry>, List<String>> summarise)
    {
        if (excludeIds.isEmpty())
        {
            return cache.computeIfAbsent(key, k -> summarise.apply(tasks.stream().filter(filter)));
        }
        return summarise.apply(tasks.stream()
            .filter(filter)
            .filter(t -> !excludeIds.contains(t.getId())));
    }

    /**
     * Every task matching all three filters, in load order. Numbered boss-kill
     * families are clustered and sorted by kill threshold. "Any" skips that
     * filter entirely.
     */
    public List<TaskEntry> allMatching(String region, String difficulty, String activityType)
    {
        return allMatching(region, difficulty, activityType, Collections.emptySet());
    }

    public List<TaskEntry> allMatching(String region, String difficulty, String activityType,
        Set<String> excludeIds)
    {
        List<TaskEntry> matched = matchCache.computeIfAbsent(cacheKey(region, difficulty, activityType), k ->
            Collections.unmodifiableList(tasks.stream()
                .filter(t -> matchesRegion(t, region) && matchesDifficulty(t, difficulty) && matchesActivityType(t, activityType))
                .collect(Collectors.toList())));

        if (excludeIds.isEmpty())
        {
            return matched;
        }

        return matched.stream()
            .filter(t -> !excludeIds.contains(t.getId()))
            .collect(Collectors.toList());
    }

    /**
     * Returns a random task matching all three filters, or empty if the
     * combination has no matches. "Any" skips that filter entirely.
     */
    public Optional<TaskEntry> randomTask(String region, String difficulty, String activityType)
    {
        return randomTask(region, difficulty, activityType, Collections.emptySet());
    }

    public Optional<TaskEntry> randomTask(String region, String difficulty, String activityType,
        Set<String> excludeIds)
    {
        List<TaskEntry> filtered = allMatching(region, difficulty, activityType, excludeIds);

        if (filtered.isEmpty())
        {
            return Optional.empty();
        }

        TaskEntry rolled = filtered.get(ThreadLocalRandom.current().nextInt(filtered.size()));
        return Optional.of(preferLowerIncompleteKill(rolled, excludeIds));
    }

    /**
     * When the picker rolls a boss kill-count task, serve the nearest lower
     * incomplete kill-count for that same boss (50 → 25 → 10 → 1). Region
     * and difficulty are ignored. A tier counts as complete when its task id
     * is in {@code completedTaskIds}.
     */
    TaskEntry preferLowerIncompleteKill(TaskEntry rolled, Set<String> completedTaskIds)
    {
        String familyKey = bossKillFamilyKeyByTaskId.get(rolled.getId());
        if (familyKey == null)
        {
            return rolled;
        }

        long rolledValue = BossKillTasks.killThreshold(rolled);
        if (rolledValue < 0)
        {
            return rolled;
        }

        List<TaskEntry> family = bossKillFamiliesByKey.get(familyKey);
        if (family == null)
        {
            return rolled;
        }

        TaskEntry best = null;
        for (TaskEntry candidate : family)
        {
            long candidateValue = BossKillTasks.killThreshold(candidate);
            if (candidateValue < 0 || candidateValue >= rolledValue)
            {
                continue;
            }
            if (completedTaskIds.contains(candidate.getId()))
            {
                continue;
            }
            if (best == null || candidateValue > BossKillTasks.killThreshold(best))
            {
                best = candidate;
            }
        }

        return best != null ? best : rolled;
    }

    /**
     * Reorders the raw dataset so each boss kill-count family appears as one
     * contiguous block, sorted by {@code autoDetect.value}. Non-family tasks
     * keep their relative order from the scrape.
     */
    private static List<TaskEntry> clusterBossKillFamilies(List<TaskEntry> loaded,
        Map<String, List<TaskEntry>> sortedFamilies, Map<String, String> familyKeyByTaskId)
    {
        List<TaskEntry> clustered = new ArrayList<>(loaded.size());
        Set<String> emittedFamilies = new HashSet<>();

        for (TaskEntry task : loaded)
        {
            String familyKey = familyKeyByTaskId.get(task.getId());
            if (familyKey == null)
            {
                clustered.add(task);
                continue;
            }

            if (emittedFamilies.add(familyKey))
            {
                clustered.addAll(sortedFamilies.get(familyKey));
            }
        }

        return Collections.unmodifiableList(clustered);
    }

    private static boolean matchesRegion(TaskEntry t, String region)
    {
        return ANY.equals(region) || region.equalsIgnoreCase(t.getRegion());
    }

    private static boolean matchesDifficulty(TaskEntry t, String difficulty)
    {
        return ANY.equals(difficulty) || difficulty.equalsIgnoreCase(t.getDifficulty());
    }

    private static boolean matchesActivityType(TaskEntry t, String activityType)
    {
        return ANY.equals(activityType) || activityType.equalsIgnoreCase(t.getActivityType());
    }

    private static List<String> distinctSorted(Stream<TaskEntry> stream, Function<TaskEntry, String> extractor)
    {
        TreeSet<String> values = stream
            .map(extractor)
            .filter(v -> v != null && !v.isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));

        List<String> result = new ArrayList<>();
        result.add(ANY);
        result.addAll(values);
        return result;
    }

    private static String cacheKey(String... parts)
    {
        return String.join("|", parts);
    }
}
