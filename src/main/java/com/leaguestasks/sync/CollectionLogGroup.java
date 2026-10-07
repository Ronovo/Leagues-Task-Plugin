package com.leaguestasks.sync;

import java.util.List;
import lombok.Value;

/** One top-level collection log tab (Bosses, Raids, Clues, ...). */
@Value
public class CollectionLogGroup
{
    String group;
    List<CollectionLogCategory> categories;
}
