package com.leaguestasks.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Reads the collection log's structure -- tabs, pages, and the item ids on
 * each page -- straight out of the game cache, so a game update that adds a
 * boss page needs no plugin change.
 *
 * <p>The cache only describes which slots <em>exist</em>. Which of them the
 * player has filled isn't in there; that comes from
 * {@link CollectionLogCapture}, which is why {@link #build(Set)} takes the
 * obtained ids as an argument.
 *
 * <p>Every method here touches the cache and must run on the client thread.
 */
@Singleton
public class CollectionLogCatalog
{
    // The enum and param ids below are cache ids with no named constant in
    // RuneLite's gameval API (it has no EnumID/ParamID tables), so unlike the
    // varbit/varp catalogs they cannot be resolved by name.

    /** Enum of structs for the top-level tabs (Bosses, Raids, ...). */
    private static final int TABS_ENUM = 2102;
    /** Tab struct param holding the tab's display name. */
    private static final int TAB_NAME_PARAM = 682;
    /** Tab struct param pointing at the enum of that tab's page structs. */
    private static final int PAGES_PARAM = 683;
    /** Page struct param holding the page's display name. */
    private static final int PAGE_NAME_PARAM = 689;
    /** Page struct param pointing at the enum of that page's item ids. */
    private static final int ITEMS_PARAM = 690;
    /**
     * Namedobj→namedobj remap the collection log UI applies before checking
     * {@code inv_620}. A handful of slots (Unsired, coal bag, gem bag, plank
     * sack, satchels, ...) declare one id on the page enum and a different
     * dummy id in the transmitted inventory. {@code collection_draw_log}
     * looks the dummy up here, then uses {@code inv_total} on that id to
     * decide opacity. We do the same per page slot so each catalog item
     * keeps its page id and filled-vs-not matches the in-game log.
     */
    private static final int SLOT_INVENTORY_REMAP_ENUM = 3721;
    /**
     * Unique obtained count shown on the in-game collection log header.
     * Dummy inventory ids fill existing page slots -- they are not extra
     * unique items -- so the unique total has to come from this varp rather
     * than from counting matched slots.
     */
    private static final int CLOG_LOGGED_VARP = VarPlayerID.COLLECTION_COUNT;

    private final Client client;

    @Inject
    public CollectionLogCatalog(Client client)
    {
        this.client = client;
    }

    /**
     * @param obtainedIds item ids the collection log search reports for
     * filled slots -- the same inventory ids {@code collection_draw_log}
     * uses for opacity, dummy ids included
     * @return the full log with each slot flagged, or null if the cache
     * isn't loaded yet (e.g. called before login)
     */
    public CollectionLogSnapshot build(Set<Integer> obtainedIds)
    {
        EnumComposition tabs = client.getEnum(TABS_ENUM);
        if (tabs == null)
        {
            return null;
        }

        // Pets and a few other items sit on more than one page, so slots
        // are counted by distinct item id to match the in-game totals.
        Set<Integer> distinctItems = new HashSet<>();
        Set<Integer> distinctObtained = new HashSet<>();
        List<CollectionLogGroup> groups = new ArrayList<>();

        for (int tabStructId : tabs.getIntVals())
        {
            StructComposition tab = client.getStructComposition(tabStructId);
            if (tab == null)
            {
                continue;
            }
            EnumComposition pages = client.getEnum(tab.getIntValue(PAGES_PARAM));
            if (pages == null)
            {
                continue;
            }

            List<CollectionLogCategory> categories = new ArrayList<>();
            for (int pageStructId : pages.getIntVals())
            {
                StructComposition page = client.getStructComposition(pageStructId);
                if (page == null)
                {
                    continue;
                }
                EnumComposition pageItems = client.getEnum(page.getIntValue(ITEMS_PARAM));
                if (pageItems == null)
                {
                    continue;
                }

                String pageName = page.getStringValue(PAGE_NAME_PARAM);
                if (pageName == null)
                {
                    continue;
                }
                List<CollectionLogItem> items = new ArrayList<>(pageItems.size());
                for (int itemId : pageItems.getIntVals())
                {
                    boolean obtained = obtainedIds.contains(inventoryIdForSlot(itemId));
                    distinctItems.add(itemId);
                    if (obtained)
                    {
                        distinctObtained.add(itemId);
                    }

                    ItemComposition item = client.getItemDefinition(itemId);
                    String itemName = item != null ? item.getName() : "";
                    items.add(new CollectionLogItem(itemId, itemName, obtained ? 1 : 0));
                }

                categories.add(new CollectionLogCategory(slug(pageName), pageName, items));
            }

            groups.add(new CollectionLogGroup(tab.getStringValue(TAB_NAME_PARAM), categories));
        }

        int uniqueObtained = client.getVarpValue(CLOG_LOGGED_VARP);
        return new CollectionLogSnapshot(
            uniqueObtained > 0 ? uniqueObtained : distinctObtained.size(),
            distinctItems.size(),
            groups);
    }

    /**
     * The id {@code collection_draw_log} checks in the collection log
     * inventory for this page-enum slot. Missing remap entries leave the
     * page id unchanged.
     */
    private int inventoryIdForSlot(int pageItemId)
    {
        EnumComposition remap = client.getEnum(SLOT_INVENTORY_REMAP_ENUM);
        if (remap == null)
        {
            return pageItemId;
        }

        int remapped = remap.getIntValue(pageItemId);
        return remapped > 0 ? remapped : pageItemId;
    }

    private static String slug(String name)
    {
        return name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }
}
