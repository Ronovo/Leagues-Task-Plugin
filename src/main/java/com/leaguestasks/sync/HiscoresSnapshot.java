package com.leaguestasks.sync;

import java.util.Collections;
import java.util.Map;

/**
 * Boss scores parsed from Jagex {@code index_lite.ws}. Scores are {@code 0}
 * when the player is unranked ({@code -1,-1} in the CSV).
 */
final class HiscoresSnapshot
{
    private static final HiscoresSnapshot EMPTY = new HiscoresSnapshot(Collections.emptyMap());

    private final Map<String, Integer> bossScores;

    HiscoresSnapshot(Map<String, Integer> bossScores)
    {
        this.bossScores = bossScores;
    }

    static HiscoresSnapshot empty()
    {
        return EMPTY;
    }

    int getBossScore(String bossName)
    {
        if (bossName == null)
        {
            return -1;
        }
        return bossScores.getOrDefault(bossName, -1);
    }

    boolean meetsThreshold(String bossName, long required)
    {
        int score = getBossScore(bossName);
        return score >= 0 && score >= required;
    }
}
