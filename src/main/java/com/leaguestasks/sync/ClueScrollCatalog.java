package com.leaguestasks.sync;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads treasure-trail completion counts per tier from the
 * {@code VarPlayerID.COMPLETED_CLUES*} run, one full-width varp per tier in
 * ascending order (easy, medium, hard, elite, master; beginner is
 * {@code COMPLETED_CLUES5} but no task needs it).
 *
 * <p>Deliberately <em>not</em> the {@code VarbitID.*_CLUES_COMPLETED_DEPRECATED}
 * constants: their tier names are stale and shifted, and medium/elite read a
 * frozen 0. Verified in-client on 2026-08-27 against the Character Summary
 * counts -- {@code COMPLETED_CLUES1} is medium, {@code COMPLETED_CLUES2} is
 * hard, etc.
 *
 * <p>Mirrors {@link BossKillCatalog}: resolve a {@code VarPlayerID} name to an
 * id once, then read on the client thread.
 */
@Singleton
public class ClueScrollCatalog
{
    private static final Logger log = LoggerFactory.getLogger(ClueScrollCatalog.class);

    /** Upper-case tier to the {@code VarPlayerID} field holding its completion count. */
    private static final Map<String, String> VARP_FIELD_BY_TIER;

    static
    {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("EASY", "COMPLETED_CLUES");
        m.put("MEDIUM", "COMPLETED_CLUES1");
        m.put("HARD", "COMPLETED_CLUES2");
        m.put("ELITE", "COMPLETED_CLUES3");
        m.put("MASTER", "COMPLETED_CLUES4");
        VARP_FIELD_BY_TIER = Collections.unmodifiableMap(m);
    }

    private final Map<String, Integer> varpIdByTier = new HashMap<>();

    @Inject
    public ClueScrollCatalog()
    {
        for (Map.Entry<String, String> entry : VARP_FIELD_BY_TIER.entrySet())
        {
            int varpId = VarbitIdFields.resolveStaticIntField(VarPlayerID.class, entry.getValue(), log);
            if (varpId >= 0)
            {
                varpIdByTier.put(entry.getKey(), varpId);
            }
        }

        if (varpIdByTier.size() != VARP_FIELD_BY_TIER.size())
        {
            log.warn("ClueScrollCatalog resolved {} of {} clue-count VarPlayer fields -- some clue tasks will not auto-detect",
                varpIdByTier.size(), VARP_FIELD_BY_TIER.size());
        }
    }

    /** False when a {@code VarPlayerID} rename left a tier unresolved. */
    public boolean isAvailable()
    {
        return varpIdByTier.size() == VARP_FIELD_BY_TIER.size();
    }

    /** Whether {@code tier} has a resolved counter. Used by the rule integrity test. */
    public boolean isTierKnown(String tier)
    {
        return tier != null && varpIdByTier.containsKey(tier.toUpperCase(Locale.ROOT));
    }

    /**
     * Completions logged for one tier, or {@code -1} when that tier has no
     * resolved counter (unknown tier name, or the field could not be resolved).
     */
    public int completionsForTier(Client client, String tier)
    {
        if (tier == null)
        {
            return -1;
        }
        Integer varpId = varpIdByTier.get(tier.toUpperCase(Locale.ROOT));
        return varpId != null ? client.getVarpValue(varpId) : -1;
    }

    // final so a mock stubbing only completionsForTier still runs the real comparison.
    final boolean meetsThreshold(Client client, String tier, long required)
    {
        int actual = completionsForTier(client, tier);
        return actual >= 0 && actual >= required;
    }
}
