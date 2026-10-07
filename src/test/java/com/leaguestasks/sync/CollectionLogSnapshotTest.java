package com.leaguestasks.sync;

import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A Gson round-trip over the model tree {@link CollectionLogCapture} reads
 * back from config via reflection (no {@code @SerializedName}s, no no-arg
 * constructors) -- the same path a cached snapshot goes through on load.
 */
public class CollectionLogSnapshotTest
{
    @Test
    public void roundTripsThroughGson()
    {
        Gson gson = new Gson();
        CollectionLogItem item = new CollectionLogItem(1234, "Tanzanite fang", 1);
        CollectionLogCategory category = new CollectionLogCategory("zulrah", "Zulrah",
            Collections.singletonList(item));
        CollectionLogGroup group = new CollectionLogGroup("Bosses", Collections.singletonList(category));
        CollectionLogSnapshot snapshot = new CollectionLogSnapshot(1, 2, Collections.singletonList(group));

        String json = gson.toJson(snapshot);
        CollectionLogSnapshot restored = gson.fromJson(json, CollectionLogSnapshot.class);

        assertEquals(1, restored.getItemsObtained());
        assertEquals(2, restored.getItemsAvailable());
        assertEquals(1, restored.getGroups().size());

        CollectionLogGroup restoredGroup = restored.getGroups().get(0);
        assertEquals("Bosses", restoredGroup.getGroup());
        assertEquals(1, restoredGroup.getCategories().size());

        CollectionLogCategory restoredCategory = restoredGroup.getCategories().get(0);
        assertEquals("zulrah", restoredCategory.getKey());
        assertEquals("Zulrah", restoredCategory.getName());
        assertEquals(1, restoredCategory.getItems().size());

        CollectionLogItem restoredItem = restoredCategory.getItems().get(0);
        assertEquals(1234, restoredItem.getId());
        assertEquals("Tanzanite fang", restoredItem.getName());
        assertEquals(1, restoredItem.getCount());
    }
}
