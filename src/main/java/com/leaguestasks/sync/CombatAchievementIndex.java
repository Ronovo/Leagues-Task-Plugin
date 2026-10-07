package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Offline wiki metadata keyed by combat achievement varbit slug. Used to
 * classify completed tasks by tier for autocomplete rules; slugs missing
 * from the index are ignored for tier counts (under-count is safe).
 */
@Singleton
public class CombatAchievementIndex
{
    private static final String RESOURCE_NAME = "/com/leaguestasks/combat_achievement_index.json";

    private final Map<String, String> tierBySlug;
    private final Map<String, Set<String>> slugsByTier;

    public CombatAchievementIndex()
    {
        this(new Gson());
    }

    @Inject
    public CombatAchievementIndex(Gson gson)
    {
        List<Entry> entries = load(gson);
        Map<String, String> tiers = new HashMap<>();
        Map<String, Set<String>> byTier = new HashMap<>();
        for (Entry entry : entries)
        {
            if (entry.slug == null || entry.tier == null)
            {
                continue;
            }
            String normalizedTier = normalizeTier(entry.tier);
            tiers.put(entry.slug, normalizedTier);
            byTier.computeIfAbsent(normalizedTier, k -> new HashSet<>()).add(entry.slug);
        }
        this.tierBySlug = Collections.unmodifiableMap(tiers);
        Map<String, Set<String>> frozen = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : byTier.entrySet())
        {
            frozen.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        this.slugsByTier = Collections.unmodifiableMap(frozen);
    }

    /** Null when the slug is not in the bundled index. */
    public String tierForSlug(String slug)
    {
        return tierBySlug.get(slug);
    }

    /** Every indexed slug for a tier, or empty when the tier is unknown. */
    public Set<String> slugsForTier(String tier)
    {
        Set<String> slugs = slugsByTier.get(normalizeTier(tier));
        return slugs != null ? slugs : Collections.emptySet();
    }

    static String normalizeTier(String tier)
    {
        if (tier == null)
        {
            return "";
        }
        return tier.trim().toUpperCase(Locale.ROOT);
    }

    private static List<Entry> load(Gson gson)
    {
        try (InputStream is = CombatAchievementIndex.class.getResourceAsStream(RESOURCE_NAME))
        {
            if (is == null)
            {
                throw new IllegalStateException(RESOURCE_NAME + " not found on classpath");
            }

            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
            {
                Type fileType = new TypeToken<IndexFile>()
                {
                }.getType();
                IndexFile file = gson.fromJson(reader, fileType);
                if (file == null || file.tasks == null)
                {
                    throw new IllegalStateException(RESOURCE_NAME + " deserialized to null");
                }
                return file.tasks;
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to load " + RESOURCE_NAME, e);
        }
    }

    private static class IndexFile
    {
        private List<Entry> tasks;
    }

    private static class Entry
    {
        private String slug;
        private String tier;
    }
}
