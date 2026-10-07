package com.leaguestasks.sync;

import lombok.Value;

/**
 * One achievement diary area/tier sync snapshot -- the area and tier slugs
 * come from {@code VarbitID} field names, plus a boolean for tier complete.
 * Gson-serialized only: written into a sync payload, never read back.
 */
@Value
public class DiaryProgress
{
    String area;
    String tier;
    boolean complete;
}
