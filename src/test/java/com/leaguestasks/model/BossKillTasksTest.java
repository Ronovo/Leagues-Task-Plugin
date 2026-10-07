package com.leaguestasks.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BossKillTasksTest
{
    @Test
    public void parsesKillCountFromFrontNumberName()
    {
        assertEquals(25, BossKillTasks.killThresholdFromName("25 Wintertodt Kills").orElse(-1));
        assertEquals(1, BossKillTasks.killThresholdFromName("1 Skotizo Kill").orElse(-1));
    }

    @Test
    public void parsesKillCountFromDefeatTimesName()
    {
        assertEquals(25, BossKillTasks.killThresholdFromName("Defeat Nex 25 Times").orElse(-1));
        assertEquals(10, BossKillTasks.killThresholdFromName("Defeat Nex 10 Times").orElse(-1));
        assertEquals(100, BossKillTasks.killThresholdFromName("Defeat The Nightmare 100 Times").orElse(-1));
        assertEquals(50, BossKillTasks.killThresholdFromName("Complete The Theatre of Blood 50 Times").orElse(-1));
    }

    @Test
    public void ignoresNonKillCountNames()
    {
        assertFalse(BossKillTasks.killThresholdFromName("Defeat Nex").isPresent());
        assertFalse(BossKillTasks.killThresholdFromName("Loot a Barrows Chest").isPresent());
    }
}
