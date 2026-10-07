package com.leaguestasks.sync;

import java.util.List;
import lombok.Value;

/**
 * One page of the collection log (Zulrah, Barrows Chests, ...).
 * {@code key} is a slug of the display name, used by the website as the
 * page's stable identifier.
 */
@Value
public class CollectionLogCategory
{
    String key;
    String name;
    List<CollectionLogItem> items;
}
