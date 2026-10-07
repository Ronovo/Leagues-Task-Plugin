package com.leaguestasks.sync;

import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BossKillCatalogTest
{
    private Client client;
    private BossKillCatalog catalog;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        catalog = new BossKillCatalog();
    }

    @Test
    public void resolvesKnownWintertodtVarp()
    {
        assertTrue(catalog.isVarpKnown("TOTAL_WINTERTODT_KILLS"));
    }

    @Test
    public void resolvesKnownMoonsOfPerilVarp()
    {
        assertTrue(catalog.isVarpKnown("TOTAL_PMOON_CHESTS"));
    }

    @Test
    public void readsKillCountFromClientVarp()
    {
        when(client.getVarpValue(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
        {
            int varpId = invocation.getArgument(0);
            if (varpId == net.runelite.api.gameval.VarPlayerID.TOTAL_WINTERTODT_KILLS)
            {
                return 42;
            }
            return 0;
        });

        assertEquals(42, catalog.getKillCount(client, "TOTAL_WINTERTODT_KILLS"));
    }
}
