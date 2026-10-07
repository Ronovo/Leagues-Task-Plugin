package com.leaguestasks.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads achievement diary tier completion from named {@code VarbitID} fields.
 * Individual tasks within a tier are not exposed this way -- only the
 * tier-complete booleans -- so this mirrors the quest sync pattern.
 *
 * <p>Every {@link #build} call must run on the client thread.
 */
@Singleton
public class AchievementDiaryCatalog
{
    private static final Logger log = LoggerFactory.getLogger(AchievementDiaryCatalog.class);
    private static final Pattern DIARY_COMPLETE =
        Pattern.compile("^(\\w+)_DIARY_(EASY|MEDIUM|HARD|ELITE)_COMPLETE$");

    private final List<DiaryVarbit> varbits;

    @Inject
    public AchievementDiaryCatalog()
    {
        varbits = discoverVarbits();
        if (varbits.isEmpty())
        {
            log.warn("AchievementDiaryCatalog matched zero VarbitID fields -- diary sync will be skipped");
        }
    }

    /** Empty when no {@code *_DIARY_*_COMPLETE} fields matched (e.g. VarbitID rename). */
    public boolean isAvailable()
    {
        return !varbits.isEmpty();
    }

    public List<DiaryProgress> build(Client client)
    {
        if (varbits.isEmpty())
        {
            return Collections.emptyList();
        }

        List<DiaryProgress> progress = new ArrayList<>(varbits.size());
        for (DiaryVarbit entry : varbits)
        {
            boolean complete = client.getVarbitValue(entry.varbitId) != 0;
            progress.add(new DiaryProgress(entry.area, entry.tier, complete));
        }
        return progress;
    }

    private static List<DiaryVarbit> discoverVarbits()
    {
        return VarbitIdFields.discover(DIARY_COMPLETE,
            (field, matcher) -> new DiaryVarbit(matcher.group(1), matcher.group(2), field.getInt(null)),
            log);
    }

    private static final class DiaryVarbit
    {
        final String area;
        final String tier;
        final int varbitId;

        DiaryVarbit(String area, String tier, int varbitId)
        {
            this.area = area;
            this.tier = tier;
            this.varbitId = varbitId;
        }
    }
}
