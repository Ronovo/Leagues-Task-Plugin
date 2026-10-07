package com.leaguestasks.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;

public class AchievementDiaryCatalogTest
{
    private AchievementDiaryCatalog catalog;
    private Client client;

    @Before
    public void setUp()
    {
        catalog = new AchievementDiaryCatalog();
        client = mock(Client.class);
    }

    @Test
    public void discoversAboutFortyFourDiaryTiers()
    {
        assertTrue(catalog.isAvailable());
        List<DiaryProgress> progress = catalog.build(client);
        assertTrue("expected about 44 diary tiers, got " + progress.size(),
            progress.size() >= 40 && progress.size() <= 48);
    }

    @Test
    public void karamjaOnlyHasEliteTier()
    {
        List<String> karamjaTiers = catalog.build(client).stream()
            .filter(d -> "KARAMJA".equals(d.getArea()))
            .map(DiaryProgress::getTier)
            .collect(Collectors.toList());

        assertEquals(1, karamjaTiers.size());
        assertEquals("ELITE", karamjaTiers.get(0));
    }

    @Test
    public void buildReadsVarbitValues()
    {
        when(client.getVarbitValue(anyInt())).thenReturn(0);
        List<DiaryProgress> progress = catalog.build(client);
        assertFalse(progress.isEmpty());

        when(client.getVarbitValue(anyInt())).thenReturn(1);
        assertTrue(catalog.build(client).stream().allMatch(DiaryProgress::isComplete));
    }
}
