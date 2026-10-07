package com.leaguestasks.sync;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the struct param regression: tab names must come from struct param 682,
 * page names from param 689. Using 689 for both produced blank group names,
 * which the tracker rejects with a 400.
 *
 * <p>Also guards per-slot inventory matching: a page slot is filled only when
 * the transmitted inventory contains the id {@code collection_draw_log} would
 * look up (enum 3721 remap, else the page id itself). Dummy ids must not be
 * treated as extra catalog entries, and must not dock the unique count.
 */
public class CollectionLogCatalogTest
{
    private static final int TABS_ENUM = 2102;
    private static final int TAB_NAME_PARAM = 682;
    private static final int PAGES_PARAM = 683;
    private static final int PAGE_NAME_PARAM = 689;
    private static final int ITEMS_PARAM = 690;
    private static final int SLOT_INVENTORY_REMAP_ENUM = 3721;
    private static final int CLOG_LOGGED_VARP = 2943;

    private static final int TAB_STRUCT_ID = 471;
    private static final int PAGE_STRUCT_ID = 501;
    private static final int PAGES_ENUM_ID = 900;
    private static final int ITEMS_ENUM_ID = 901;
    private static final int ITEM_ID = 1234;
    private static final int UNSIRED_PAGE_ID = 13273;
    private static final int UNSIRED_DUMMY_ID = 25624;

    private Client client;
    private CollectionLogCatalog catalog;

    @Before
    public void setUp()
    {
        client = mock(Client.class);

        EnumComposition tabsEnum = mock(EnumComposition.class);
        when(tabsEnum.getIntVals()).thenReturn(new int[] {TAB_STRUCT_ID});
        when(client.getEnum(TABS_ENUM)).thenReturn(tabsEnum);

        StructComposition tab = mock(StructComposition.class);
        when(tab.getIntValue(PAGES_PARAM)).thenReturn(PAGES_ENUM_ID);
        when(tab.getStringValue(TAB_NAME_PARAM)).thenReturn("Bosses");
        when(client.getStructComposition(TAB_STRUCT_ID)).thenReturn(tab);

        EnumComposition pagesEnum = mock(EnumComposition.class);
        when(pagesEnum.getIntVals()).thenReturn(new int[] {PAGE_STRUCT_ID});
        when(client.getEnum(PAGES_ENUM_ID)).thenReturn(pagesEnum);

        StructComposition page = mock(StructComposition.class);
        when(page.getIntValue(ITEMS_PARAM)).thenReturn(ITEMS_ENUM_ID);
        when(page.getStringValue(PAGE_NAME_PARAM)).thenReturn("Zulrah");
        when(client.getStructComposition(PAGE_STRUCT_ID)).thenReturn(page);

        EnumComposition itemsEnum = mock(EnumComposition.class);
        when(itemsEnum.getIntVals()).thenReturn(new int[] {ITEM_ID});
        when(itemsEnum.size()).thenReturn(1);
        when(client.getEnum(ITEMS_ENUM_ID)).thenReturn(itemsEnum);

        ItemComposition item = mock(ItemComposition.class);
        when(item.getName()).thenReturn("Tanzanite fang");
        when(client.getItemDefinition(ITEM_ID)).thenReturn(item);

        EnumComposition remap = mock(EnumComposition.class);
        when(remap.getIntValue(anyInt())).thenReturn(0);
        when(client.getEnum(SLOT_INVENTORY_REMAP_ENUM)).thenReturn(remap);

        catalog = new CollectionLogCatalog(client);
    }

    @Test
    public void tabNameReadsParam682NotParam689()
    {
        CollectionLogSnapshot snapshot = catalog.build(Collections.emptySet());

        assertEquals(1, snapshot.getGroups().size());
        assertEquals("Bosses", snapshot.getGroups().get(0).getGroup());
    }

    @Test
    public void pageNameReadsParam689NotParam682()
    {
        CollectionLogSnapshot snapshot = catalog.build(Collections.emptySet());

        CollectionLogCategory category = snapshot.getGroups().get(0).getCategories().get(0);
        assertEquals("Zulrah", category.getName());
        assertEquals("zulrah", category.getKey());
    }

    @Test
    public void obtainedAndAvailableCountsUseDistinctItemIds()
    {
        CollectionLogSnapshot snapshot = catalog.build(Collections.singleton(ITEM_ID));

        assertEquals(1, snapshot.getItemsObtained());
        assertEquals(1, snapshot.getItemsAvailable());
        assertEquals(ITEM_ID, snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0).getId());
        assertEquals(1, snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0).getCount());
    }

    @Test
    public void dummyInventoryIdDoesNotFillAnUnrelatedPageSlot()
    {
        CollectionLogSnapshot snapshot = catalog.build(Collections.singleton(UNSIRED_DUMMY_ID));

        assertEquals(0, snapshot.getItemsObtained());
        assertEquals(1, snapshot.getItemsAvailable());
        assertEquals(0, snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0).getCount());
    }

    private void stubRemappedUnsiredSlot()
    {
        EnumComposition itemsEnum = mock(EnumComposition.class);
        when(itemsEnum.getIntVals()).thenReturn(new int[] {UNSIRED_PAGE_ID});
        when(itemsEnum.size()).thenReturn(1);
        when(client.getEnum(ITEMS_ENUM_ID)).thenReturn(itemsEnum);

        ItemComposition item = mock(ItemComposition.class);
        when(item.getName()).thenReturn("Unsired");
        when(client.getItemDefinition(UNSIRED_PAGE_ID)).thenReturn(item);

        EnumComposition remap = mock(EnumComposition.class);
        when(remap.getIntValue(anyInt())).thenReturn(0);
        when(remap.getIntValue(UNSIRED_PAGE_ID)).thenReturn(UNSIRED_DUMMY_ID);
        when(client.getEnum(SLOT_INVENTORY_REMAP_ENUM)).thenReturn(remap);
    }

    @Test
    public void remappedSlotIsFilledByItsInventoryIdAndKeepsThePageId()
    {
        stubRemappedUnsiredSlot();

        CollectionLogSnapshot snapshot = catalog.build(Collections.singleton(UNSIRED_DUMMY_ID));

        assertEquals(1, snapshot.getItemsObtained());
        assertEquals(1, snapshot.getItemsAvailable());
        CollectionLogItem slot = snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0);
        assertEquals(UNSIRED_PAGE_ID, slot.getId());
        assertEquals(1, slot.getCount());
    }

    @Test
    public void uniqueObtainedUsesTheInGameHeaderVarpNotMatchedDummySlots()
    {
        stubRemappedUnsiredSlot();
        when(client.getVarpValue(CLOG_LOGGED_VARP)).thenReturn(690);

        CollectionLogSnapshot snapshot = catalog.build(Collections.singleton(UNSIRED_DUMMY_ID));

        assertEquals(690, snapshot.getItemsObtained());
        assertEquals(1, snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0).getCount());
    }

    @Test
    public void remappedSlotIsNotFilledByItsPageId()
    {
        stubRemappedUnsiredSlot();

        // The page id sitting in the inventory would be a collision, not
        // ownership of this slot -- collection_draw_log only checks the dummy.
        CollectionLogSnapshot snapshot = catalog.build(Collections.singleton(UNSIRED_PAGE_ID));

        assertEquals(0, snapshot.getItemsObtained());
        assertEquals(0, snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0).getCount());
    }

    @Test
    public void duplicatePageSlotsCountOnce()
    {
        EnumComposition itemsEnum = mock(EnumComposition.class);
        when(itemsEnum.getIntVals()).thenReturn(new int[] {ITEM_ID, ITEM_ID});
        when(itemsEnum.size()).thenReturn(2);
        when(client.getEnum(ITEMS_ENUM_ID)).thenReturn(itemsEnum);

        CollectionLogSnapshot snapshot = catalog.build(Set.of(ITEM_ID));

        assertEquals(1, snapshot.getItemsObtained());
        assertEquals(1, snapshot.getItemsAvailable());
    }

    @Test
    public void nullCacheReturnsNull()
    {
        Client empty = mock(Client.class);
        when(empty.getEnum(TABS_ENUM)).thenReturn(null);

        assertNull(new CollectionLogCatalog(empty).build(Collections.emptySet()));
    }

    @Test
    public void missingStructOrItemDefinitionIsSkippedNotThrown()
    {
        // Cache half-loaded: the tab struct and the item definition come
        // back null. build() must skip those slots, not NPE every tick.
        when(client.getStructComposition(TAB_STRUCT_ID)).thenReturn(null);

        CollectionLogSnapshot snapshot = catalog.build(Collections.emptySet());

        assertEquals(0, snapshot.getGroups().size());
        assertEquals(0, snapshot.getItemsAvailable());
    }

    @Test
    public void missingItemDefinitionLeavesTheSlotWithABlankName()
    {
        when(client.getItemDefinition(ITEM_ID)).thenReturn(null);

        CollectionLogSnapshot snapshot = catalog.build(Collections.emptySet());

        CollectionLogItem slot = snapshot.getGroups().get(0).getCategories().get(0).getItems().get(0);
        assertEquals(ITEM_ID, slot.getId());
        assertEquals("", slot.getName());
    }
}
