package com.leaguestasks.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HiscoresKillCatalogTest
{
    /** {@code index_lite.ws} line index for Skotizo (25 skills + 20 pre-boss + 46). */
    private static final int SKOTIZO_LINE = 91;

    @Test
    public void parsesSkotizoScoreFromCsvLayout()
    {
        HiscoresSnapshot snapshot = HiscoresKillCatalog.parseCsv(csvWithScoreAtLine(SKOTIZO_LINE, 37));

        assertEquals(37, snapshot.getBossScore("Skotizo"));
        assertTrue(snapshot.meetsThreshold("Skotizo", 25));
        assertFalse(snapshot.meetsThreshold("Skotizo", 50));
    }

    @Test
    public void treatsUnrankedCsvLineAsZero()
    {
        HiscoresSnapshot snapshot = HiscoresKillCatalog.parseCsv(csvWithScoreAtLine(SKOTIZO_LINE, -1));

        assertEquals(0, snapshot.getBossScore("Skotizo"));
        assertTrue(snapshot.meetsThreshold("Skotizo", 0));
        assertFalse(snapshot.meetsThreshold("Skotizo", 1));
    }

    @Test
    public void knowsSkotizoBossName()
    {
        HiscoresKillCatalog catalog = new HiscoresKillCatalog(new okhttp3.OkHttpClient());
        assertTrue(catalog.isBossKnown("Skotizo"));
    }

    private static String csvWithScoreAtLine(int lineIndex, int score)
    {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < lineIndex; i++)
        {
            csv.append("-1,0,0\n");
        }
        csv.append("-1,").append(score).append('\n');
        return csv.toString();
    }
}
