package com.leaguestasks.sync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads boss and minigame completion counts from named {@link
 * net.runelite.api.gameval.VarPlayerID} fields via {@link Client#getVarpValue}.
 * Every {@link #getKillCount} call must run on the client thread.
 */
@Singleton
public class BossKillCatalog
{
    private static final Logger log = LoggerFactory.getLogger(BossKillCatalog.class);

    /** Resolved varp ids for every field name this catalog has been asked for. */
    private final Map<String, Integer> varpIds = new HashMap<>();

    @Inject
    public BossKillCatalog()
    {
    }

    /** False when the named {@code VarPlayerID} field could not be resolved. */
    public boolean isVarpKnown(String varpFieldName)
    {
        return resolveVarpId(varpFieldName) >= 0;
    }

    /**
     * Kill or completion count for one varp field, or {@code -1} when the
     * field name is unknown.
     */
    public int getKillCount(Client client, String varpFieldName)
    {
        int varpId = resolveVarpId(varpFieldName);
        if (varpId < 0)
        {
            return -1;
        }
        return client.getVarpValue(varpId);
    }

    // The threshold helpers are final so a mock stubbing only getKillCount
    // still runs the real comparisons.
    final boolean meetsThreshold(Client client, String varpFieldName, long required)
    {
        int actual = getKillCount(client, varpFieldName);
        return actual >= 0 && actual >= required;
    }

    final boolean allMeetThreshold(Client client, List<String> varpFieldNames, long required)
    {
        if (varpFieldNames == null || varpFieldNames.isEmpty())
        {
            return false;
        }

        for (String field : varpFieldNames)
        {
            if (!meetsThreshold(client, field, required))
            {
                return false;
            }
        }
        return true;
    }

    final boolean anyMeetsThreshold(Client client, List<String> varpFieldNames, long required)
    {
        if (varpFieldNames == null || varpFieldNames.isEmpty())
        {
            return false;
        }

        for (String field : varpFieldNames)
        {
            if (meetsThreshold(client, field, required))
            {
                return true;
            }
        }
        return false;
    }

    private int resolveVarpId(String varpFieldName)
    {
        if (varpFieldName == null || varpFieldName.isEmpty())
        {
            return -1;
        }

        Integer cached = varpIds.get(varpFieldName);
        if (cached != null)
        {
            return cached;
        }

        int resolved = VarbitIdFields.resolveStaticIntField(VarPlayerID.class, varpFieldName, log);
        varpIds.put(varpFieldName, resolved);
        return resolved;
    }
}
