package com.leaguestasks.sync;

import java.util.List;
import lombok.Value;

/**
 * The player's whole collection log at one point in time: every slot the
 * game knows about, flagged obtained or missing.
 *
 * <p>The counts are of <em>distinct</em> item ids, matching the totals the
 * game shows -- a pet that appears on several pages is one item, not one
 * per page.
 */
@Value
public class CollectionLogSnapshot
{
    int itemsObtained;
    int itemsAvailable;
    List<CollectionLogGroup> groups;
}
