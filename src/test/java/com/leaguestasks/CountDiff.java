package com.leaguestasks;

import java.util.Map;
import java.util.TreeSet;

/** Readable "what moved" summary for the pinned-count integrity tests. */
public final class CountDiff
{
    private CountDiff()
    {
    }

    /** One {@code key: expected -> actual} line per key whose count differs; empty when equal. */
    public static String describe(Map<String, Integer> expected, Map<String, Integer> actual)
    {
        StringBuilder out = new StringBuilder();
        TreeSet<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        for (String key : keys)
        {
            int e = expected.getOrDefault(key, 0);
            int a = actual.getOrDefault(key, 0);
            if (e != a)
            {
                out.append(System.lineSeparator()).append("  ").append(key).append(": ")
                    .append(e).append(" -> ").append(a);
            }
        }
        return out.toString();
    }
}
