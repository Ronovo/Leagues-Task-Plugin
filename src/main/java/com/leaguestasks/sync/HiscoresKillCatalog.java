package com.leaguestasks.sync;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches boss kill counts from Jagex hiscores for bosses that have no
 * {@code VarPlayerID} counter in the client (e.g. Skotizo). Parsed using
 * the same CSV layout as {@code Personal-RuneScape-Tracker}'s hiscores
 * module: 25 skill lines, then activity lines in fixed order with bosses
 * starting at activity index 20.
 */
@Singleton
public class HiscoresKillCatalog
{
    private static final Logger log = LoggerFactory.getLogger(HiscoresKillCatalog.class);

    private static final int SKILLS_COUNT = 25;
    /** Activity lines before the boss block in {@code index_lite.ws}. */
    private static final int PRE_BOSS_ACTIVITY_COUNT = 20;

    private static final List<String> BOSSES = Arrays.asList(
        "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio",
        "Barrows Chests", "Brutus", "Bryophyta", "Callisto", "Calvar'ion",
        "Cerberus", "Chambers of Xeric", "Chambers of Xeric: Challenge Mode",
        "Chaos Elemental", "Chaos Fanatic", "Commander Zilyana", "Corporeal Beast",
        "Crazy Archaeologist", "Dagannoth Prime", "Dagannoth Rex",
        "Dagannoth Supreme", "Deranged Archaeologist", "Doom of Mokhaiotl",
        "Duke Sucellus", "General Graardor", "Giant Mole", "Grotesque Guardians",
        "Hespori", "Kalphite Queen", "King Black Dragon", "Kraken", "Kree'Arra",
        "K'ril Tsutsaroth", "Lunar Chests", "Mad Angel", "Maggot King", "Mimic",
        "Nex", "Nightmare", "Phosani's Nightmare", "Obor", "Phantom Muspah",
        "Sarachnis", "Scorpia", "Scurrius", "Shellbane Gryphon", "Skotizo",
        "Sol Heredit", "Spindel", "Tempoross", "The Gauntlet",
        "The Corrupted Gauntlet", "The Hueycoatl", "The Leviathan",
        "The Royal Titans", "The Whisperer", "Theatre of Blood",
        "Theatre of Blood: Hard Mode", "Thermonuclear Smoke Devil",
        "Tombs of Amascut", "Tombs of Amascut: Expert Mode", "TzKal-Zuk",
        "TzTok-Jad", "Vardorvis", "Venenatis", "Vet'ion", "Vorkath", "Wintertodt",
        "Yama", "Zalcano", "Zulrah"
    );

    private static final Map<String, Integer> BOSS_LINE_INDEX = buildBossLineIndices();

    private static final long CACHE_TTL_MS = 60_000L;

    private final OkHttpClient okHttpClient;

    /**
     * The whole cache as one immutable value behind a single volatile
     * reference, so a lookup right after a character switch can never splice
     * a fresh username onto the previous character's scores.
     */
    private volatile CacheEntry cache;

    @Inject
    HiscoresKillCatalog(OkHttpClient okHttpClient)
    {
        this.okHttpClient = okHttpClient;
    }

    private static final class CacheEntry
    {
        final String username;
        final HiscoresSnapshot snapshot;
        final long fetchedAtMs;

        CacheEntry(String username, HiscoresSnapshot snapshot, long fetchedAtMs)
        {
            this.username = username;
            this.snapshot = snapshot;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    /** Whether {@code bossName} is a hiscores boss this catalog knows how to read. */
    public boolean isBossKnown(String bossName)
    {
        return bossName != null && BOSS_LINE_INDEX.containsKey(bossName);
    }

    /**
     * Loads hiscores for {@code username} (from cache when fresh), then
     * invokes {@code callback} on OkHttp's thread. Failures yield an empty
     * snapshot rather than breaking autocomplete.
     */
    void prefetch(String username, Consumer<HiscoresSnapshot> callback)
    {
        if (username == null || username.isEmpty())
        {
            callback.accept(HiscoresSnapshot.empty());
            return;
        }

        long now = System.currentTimeMillis();
        CacheEntry current = cache;
        if (current != null
            && username.equals(current.username)
            && now - current.fetchedAtMs < CACHE_TTL_MS)
        {
            callback.accept(current.snapshot);
            return;
        }

        HttpUrl url = HttpUrl.parse("https://secure.runescape.com/m=hiscore_oldschool/index_lite.ws")
            .newBuilder()
            .addQueryParameter("player", username)
            .build();

        okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Hiscores lookup failed for {}", username, e);
                callback.accept(HiscoresSnapshot.empty());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (ResponseBody body = response.body())
                {
                    if (!response.isSuccessful() || body == null)
                    {
                        log.debug("Hiscores lookup returned HTTP {} for {}", response.code(), username);
                        callback.accept(HiscoresSnapshot.empty());
                        return;
                    }

                    HiscoresSnapshot snapshot = parseCsv(body.string());
                    cache = new CacheEntry(username, snapshot, System.currentTimeMillis());
                    callback.accept(snapshot);
                }
            }
        });
    }

    static HiscoresSnapshot parseCsv(String csv)
    {
        String[] lines = csv.trim().split("\\R");
        if (lines.length < SKILLS_COUNT + PRE_BOSS_ACTIVITY_COUNT)
        {
            return HiscoresSnapshot.empty();
        }

        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : BOSS_LINE_INDEX.entrySet())
        {
            int lineIndex = entry.getValue();
            if (lineIndex >= lines.length)
            {
                continue;
            }

            String[] parts = lines[lineIndex].split(",", -1);
            if (parts.length < 2)
            {
                continue;
            }

            scores.put(entry.getKey(), parseScore(parts[1]));
        }

        return new HiscoresSnapshot(scores);
    }

    private static int parseScore(String raw)
    {
        try
        {
            int score = Integer.parseInt(raw.trim());
            return score < 0 ? 0 : score;
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static Map<String, Integer> buildBossLineIndices()
    {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < BOSSES.size(); i++)
        {
            int activityIndex = PRE_BOSS_ACTIVITY_COUNT + i;
            indices.put(BOSSES.get(i), SKILLS_COUNT + activityIndex);
        }
        return indices;
    }
}
