package com.leaguestasks.sync;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Quest;

/**
 * Resolves the quest titles written in {@code leagues_tasks.json} (wiki
 * spelling) to RuneLite's {@link Quest} constants.
 */
@Singleton
public class QuestNameIndex
{
    /** Right single quotation mark, as the wiki writes apostrophes. */
    private static final char CURLY_APOSTROPHE = '\u2019';

    private final Map<String, Quest> questsByName;

    @Inject
    public QuestNameIndex()
    {
        Map<String, Quest> byName = new HashMap<>();
        for (Quest quest : Quest.values())
        {
            byName.put(normalize(quest.getName()), quest);
        }
        questsByName = Collections.unmodifiableMap(byName);
    }

    /** Null when RuneLite has no matching constant. */
    public Quest find(String title)
    {
        return title == null ? null : questsByName.get(normalize(title));
    }

    /**
     * Folds away the punctuation that differs between the wiki title and
     * RuneLite's enum (curly vs straight apostrophes, hyphens, spacing).
     */
    static String normalize(String name)
    {
        if (name == null)
        {
            return "";
        }

        String folded = name.toLowerCase(Locale.ROOT)
            .replace(CURLY_APOSTROPHE, '\'')
            .replaceAll("[^a-z0-9]+", "");

        // The wiki and RuneLite disagree about the leading article on a
        // dozen quests ("Queen of Thieves" vs "The Queen of Thieves",
        // "The observatory quest" vs "Observatory Quest"), so drop it
        // from both sides. Safe because no two quests differ only by it.
        return folded.startsWith("the") ? folded.substring(3) : folded;
    }
}
