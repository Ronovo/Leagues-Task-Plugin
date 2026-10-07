package com.leaguestasks.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

public class CombatAchievementCatalogTest
{
    private CombatAchievementCatalog catalog;
    private Client client;

    @Before
    public void setUp()
    {
        catalog = new CombatAchievementCatalog();
        client = mock(Client.class);
    }

    @Test
    public void discoversAboutThreeHundredNinetyNineTasks()
    {
        assertTrue(catalog.isAvailable());
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        CombatAchievementSnapshot snapshot = catalog.build(client);
        assertTrue("expected about 399 CA tasks, got " + snapshot.getTasks().size(),
            snapshot.getTasks().size() >= 390 && snapshot.getTasks().size() <= 410);
    }

    @Test
    public void buildReadsPointsAndCompletionFlags()
    {
        when(client.getVarbitValue(anyInt())).thenReturn(0);

        CombatAchievementSnapshot snapshot = catalog.build(client);
        assertFalse(snapshot.getTasks().isEmpty());

        when(client.getVarbitValue(anyInt())).thenReturn(1);
        snapshot = catalog.build(client);
        assertEquals(1, snapshot.getPoints());
        assertTrue(snapshot.getTasks().stream().allMatch(CombatAchievementTask::isComplete));
    }
}
