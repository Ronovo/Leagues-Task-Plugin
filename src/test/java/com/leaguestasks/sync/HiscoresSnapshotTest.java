package com.leaguestasks.sync;

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HiscoresSnapshotTest
{
    private final HiscoresSnapshot snapshot = new HiscoresSnapshot(Collections.singletonMap("Skotizo", 10));

    @Test
    public void knownBossComparesAgainstScore()
    {
        assertEquals(10, snapshot.getBossScore("Skotizo"));
        assertTrue(snapshot.meetsThreshold("Skotizo", 10));
        assertFalse(snapshot.meetsThreshold("Skotizo", 11));
    }

    @Test
    public void unknownOrNullBossNeverMeetsAThreshold()
    {
        assertEquals(-1, snapshot.getBossScore("Obor"));
        assertFalse(snapshot.meetsThreshold("Obor", 0));
        assertFalse(snapshot.meetsThreshold(null, 0));
    }

    @Test
    public void emptySnapshotMeetsNothing()
    {
        assertFalse(HiscoresSnapshot.empty().meetsThreshold("Skotizo", 0));
    }
}
