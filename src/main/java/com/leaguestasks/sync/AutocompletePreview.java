package com.leaguestasks.sync;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * What an autocomplete pass would mark, computed without writing anything.
 * Built on the client thread by {@link TaskAutoDetector} and handed to the
 * EDT for display, so it holds only plain values -- never a live client
 * reference.
 *
 * <p>{@link #getUnavailableReason()} is non-null when no count could be
 * taken at all (not logged in), which the card shows in place of a
 * breakdown. A logged-in player with nothing left to detect is a different
 * thing: reason null, total zero.
 */
public class AutocompletePreview
{
    /** One row of the breakdown: a rule category and how many are pending. */
    @Value
    public static class CategoryCount
    {
        String label;
        int count;
    }

    private final int total;
    private final List<CategoryCount> categories;
    private final String unavailableReason;

    private AutocompletePreview(int total, List<CategoryCount> categories, String unavailableReason)
    {
        this.total = total;
        this.categories = categories;
        this.unavailableReason = unavailableReason;
    }

    public static AutocompletePreview of(int total, List<CategoryCount> categories)
    {
        return new AutocompletePreview(total, Collections.unmodifiableList(categories), null);
    }

    public static AutocompletePreview unavailable(String reason)
    {
        return new AutocompletePreview(0, Collections.emptyList(), reason);
    }

    public int getTotal()
    {
        return total;
    }

    /** Non-empty categories only, in a fixed display order. */
    public List<CategoryCount> getCategories()
    {
        return categories;
    }

    public String getUnavailableReason()
    {
        return unavailableReason;
    }
}
