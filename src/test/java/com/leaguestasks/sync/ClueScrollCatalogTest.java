package com.leaguestasks.sync;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClueScrollCatalogTest
{
    private Client client;
    private ClueScrollCatalog catalog;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        catalog = new ClueScrollCatalog();
    }

    @Test
    public void resolvesEveryPerTierClueVarp()
    {
        assertTrue(catalog.isAvailable());
        for (String tier : new String[] {"EASY", "MEDIUM", "HARD", "ELITE", "MASTER"})
        {
            assertTrue(tier, catalog.isTierKnown(tier));
        }
    }

    @Test
    public void doesNotKnowBeginnerOrGarbage()
    {
        assertFalse(catalog.isTierKnown("BEGINNER"));
        assertFalse(catalog.isTierKnown("nonsense"));
        assertFalse(catalog.isTierKnown(null));
    }

    @Test
    public void readsPerTierCountFromClientVarp()
    {
        when(client.getVarpValue(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
        {
            int varpId = invocation.getArgument(0);
            // COMPLETED_CLUES2 is the hard-tier counter (RuneLite's VarbitID names are stale).
            return varpId == VarPlayerID.COMPLETED_CLUES2 ? 63 : 0;
        });

        assertEquals(63, catalog.completionsForTier(client, "hard"));
        assertTrue(catalog.meetsThreshold(client, "HARD", 50));
        assertFalse(catalog.meetsThreshold(client, "HARD", 75));
    }

    @Test
    public void unknownTierReadsNegative()
    {
        assertEquals(-1, catalog.completionsForTier(client, "BEGINNER"));
        assertFalse(catalog.meetsThreshold(client, "BEGINNER", 1));
    }
}
