package com.leaguestasks.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CombatAchievementIndexTest
{
    private final CombatAchievementIndex index = new CombatAchievementIndex();

    @Test
    public void resolvesKnownSlugTier()
    {
        assertEquals("HARD", index.tierForSlug("ABYSSALSIRE_KILLCOUNT_1"));
    }

    @Test
    public void slugsForTierIncludesKnownSlug()
    {
        assertTrue(index.slugsForTier("Hard").contains("ABYSSALSIRE_KILLCOUNT_1"));
    }

    @Test
    public void unknownSlugReturnsNull()
    {
        assertNull(index.tierForSlug("NOT_A_REAL_CA_TASK"));
    }

    @Test
    public void bundledIndexHasExpectedSize()
    {
        assertNotNull(index.tierForSlug("SNAKEBOSS_KILLCOUNT_1"));
        assertTrue(index.slugsForTier("EASY").size() > 10);
    }
}
