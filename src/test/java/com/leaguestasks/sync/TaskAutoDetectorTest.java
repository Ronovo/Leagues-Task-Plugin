package com.leaguestasks.sync;

import com.google.gson.Gson;
import com.leaguestasks.InMemoryConfig;
import com.leaguestasks.model.CompletedTaskRecord;
import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskRepository;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the rule evaluators through {@link TaskAutoDetector#preview}
 * and {@link TaskAutoDetector#apply}, including quest rules -- those run a
 * clientscript and read the int stack, which is stubbed below. That the
 * shipped quest <em>titles</em> resolve to real constants is a separate
 * question, checked by {@link AutoDetectRuleIntegrityTest}.
 */
public class TaskAutoDetectorTest
{
    private static final int QUEST_NOT_STARTED = 1;
    private static final int QUEST_FINISHED = 2;

    private Client client;
    private CompletedTaskStore store;
    private TaskAutoDetector detector;
    private ClientThread clientThread;

    @Before
    public void setUp()
    {
        client = mock(Client.class, RETURNS_DEEP_STUBS);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        Map<String, String> config = new HashMap<>();
        ConfigManager configManager = InMemoryConfig.backedBy(config);

        // ClientThread.invoke runs inline; the detector then delivers its
        // result via SwingUtilities.invokeLater, which flushEdt() drains.
        clientThread = mock(ClientThread.class);
        doAnswer(i ->
        {
            i.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));

        // Quest.getState() runs a script and reads client.getIntStack()[0]:
        // 2 FINISHED, 1 NOT_STARTED, anything else IN_PROGRESS. A deep-stub
        // mock hands back null for an int[], so this has to be explicit.
        when(client.getIntStack()).thenReturn(new int[]{QUEST_NOT_STARTED});

        store = new CompletedTaskStore(configManager, new Gson());
        AchievementDiaryCatalog diaryCatalog = mock(AchievementDiaryCatalog.class);
        CombatAchievementCatalog combatCatalog = mock(CombatAchievementCatalog.class);
        BossKillCatalog bossKillCatalog = mock(BossKillCatalog.class);
        when(diaryCatalog.isAvailable()).thenReturn(false);
        when(combatCatalog.isAvailable()).thenReturn(false);
        detector = buildDetector(diaryCatalog, combatCatalog, bossKillCatalog, mockHiscores());
    }

    private static HiscoresKillCatalog mockHiscores()
    {
        HiscoresKillCatalog hiscoresKillCatalog = mock(HiscoresKillCatalog.class);
        doAnswer(invocation ->
        {
            Consumer<HiscoresSnapshot> callback = invocation.getArgument(1);
            callback.accept(HiscoresSnapshot.empty());
            return null;
        }).when(hiscoresKillCatalog).prefetch(anyString(), any());
        return hiscoresKillCatalog;
    }

    private TaskAutoDetector buildDetector(
        AchievementDiaryCatalog diaryCatalog,
        CombatAchievementCatalog combatCatalog,
        BossKillCatalog bossKillCatalog)
    {
        return buildDetector(diaryCatalog, combatCatalog, bossKillCatalog, mockHiscores());
    }

    private TaskAutoDetector buildDetector(
        AchievementDiaryCatalog diaryCatalog,
        CombatAchievementCatalog combatCatalog,
        BossKillCatalog bossKillCatalog,
        HiscoresKillCatalog hiscoresKillCatalog)
    {
        return buildDetector(diaryCatalog, combatCatalog, bossKillCatalog,
            mock(ClueScrollCatalog.class), hiscoresKillCatalog);
    }

    private TaskAutoDetector buildDetector(
        AchievementDiaryCatalog diaryCatalog,
        CombatAchievementCatalog combatCatalog,
        BossKillCatalog bossKillCatalog,
        ClueScrollCatalog clueScrollCatalog,
        HiscoresKillCatalog hiscoresKillCatalog)
    {
        AutoDetectGameStateFactory gameStateFactory = new AutoDetectGameStateFactory(client,
            diaryCatalog, combatCatalog, new CombatAchievementIndex(), bossKillCatalog,
            clueScrollCatalog, new QuestNameIndex());
        return new TaskAutoDetector(client, clientThread, mock(EventBus.class),
            new TaskRepository(new Gson()), store, mock(SavedTaskStore.class), mock(CollectionLogCapture.class),
            hiscoresKillCatalog, gameStateFactory);
    }

    /** Every skill reads {@code level}, and Attack additionally gets {@code attackXp}. */
    private void stats(int level, int attackXp, int combatLevel)
    {
        for (Skill skill : Skill.values())
        {
            when(client.getRealSkillLevel(skill)).thenReturn(level);
            when(client.getSkillExperience(skill)).thenReturn(0);
        }
        when(client.getSkillExperience(Skill.ATTACK)).thenReturn(attackXp);
        when(client.getTotalLevel()).thenReturn(level * 23);

        Player player = mock(Player.class);
        when(player.getCombatLevel()).thenReturn(combatLevel);
        when(player.getName()).thenReturn("Zezima");
        when(client.getLocalPlayer()).thenReturn(player);
    }

    /**
     * Waits for everything already queued on the EDT, which is where the
     * detector delivers its callbacks. invokeLater is FIFO, so an empty
     * invokeAndWait queued afterwards cannot run before them.
     */
    private static void flushEdt()
    {
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
            });
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for the EDT", e);
        }
        catch (InvocationTargetException e)
        {
            throw new AssertionError(e);
        }
    }

    private AutocompletePreview preview()
    {
        AtomicReference<AutocompletePreview> result = new AtomicReference<>();
        detector.preview(result::set);
        flushEdt();
        assertNotNull("preview callback must fire exactly once", result.get());
        return result.get();
    }

    private Set<String> applyAndGetCompleted()
    {
        AtomicInteger marked = new AtomicInteger(-1);
        detector.apply(marked::set);
        flushEdt();
        assertTrue("apply callback must fire", marked.get() >= 0);
        return store.completedTaskIds();
    }

    @Test
    public void previewWritesNothing()
    {
        stats(99, 200_000_000, 126);

        assertTrue(preview().getTotal() > 0);
        assertTrue("preview must not mark anything", store.completedTaskIds().isEmpty());
    }

    @Test
    public void levelRulesFireOnlyAtOrAboveTheThreshold()
    {
        stats(19, 0, 1);
        assertFalse("level 19 must not satisfy a level 20 rule",
            applyAndGetCompleted().contains("level-20-attack"));

        stats(20, 0, 1);
        assertTrue("level 20 satisfies a level 20 rule",
            applyAndGetCompleted().contains("level-20-attack"));
    }

    @Test
    public void xpRulesFireOnlyAtOrAboveTheThreshold()
    {
        stats(99, 24_999_999, 1);
        assertFalse(applyAndGetCompleted().contains("obtain-25-million-attack-xp"));

        stats(99, 25_000_000, 1);
        assertTrue(applyAndGetCompleted().contains("obtain-25-million-attack-xp"));
    }

    @Test
    public void baseAndTotalAndAnySkillRulesReadTheRightAggregate()
    {
        // Every skill at 9: base level 10 is not met, and 9*23=207 total is
        // short of 250, but 9*23=207 total level already clears the 36
        // "first level 5" threshold (see firstLevelTasksUseATotalLevelDelta
        // for the boundary itself).
        stats(9, 0, 1);
        Set<String> low = applyAndGetCompleted();
        assertFalse("base level 10 needs every skill at 10", low.contains("base-level-10"));
        assertFalse("207 total is short of 250", low.contains("250-total-level"));
        assertTrue("207 total clears the level-5 threshold of 36", low.contains("achieve-your-first-level-5"));

        stats(11, 0, 1);
        Set<String> high = applyAndGetCompleted();
        assertTrue(high.contains("base-level-10"));
        assertTrue("11*23=253 clears 250 total", high.contains("250-total-level"));
    }

    /**
     * Hitpoints starts at level 10 for every character, so a fresh account's
     * total level is 22 other trainable skills at 1 + Hitpoints at 10 = 32.
     * "Achieve Your First Level Up/5/10" use TOTAL_LEVEL thresholds of
     * 32 + (N - 1) instead of a plain per-skill check specifically so they
     * aren't trivially already true at character creation.
     */
    @Test
    public void firstLevelTasksUseATotalLevelDelta()
    {
        stats(1, 0, 1);
        when(client.getTotalLevel()).thenReturn(32);
        Set<String> fresh = applyAndGetCompleted();
        assertFalse("a fresh account must not satisfy first-level-up",
            fresh.contains("achieve-your-first-level-up"));
        assertFalse("a fresh account must not satisfy first-level-5",
            fresh.contains("achieve-your-first-level-5"));
        assertFalse("a fresh account must not satisfy first-level-10",
            fresh.contains("achieve-your-first-level-10"));

        stats(1, 0, 1);
        when(client.getTotalLevel()).thenReturn(33);
        assertTrue("any single level-up satisfies first-level-up",
            applyAndGetCompleted().contains("achieve-your-first-level-up"));

        stats(1, 0, 1);
        when(client.getTotalLevel()).thenReturn(36);
        assertTrue("+4 total level satisfies first-level-5",
            applyAndGetCompleted().contains("achieve-your-first-level-5"));

        stats(1, 0, 1);
        when(client.getTotalLevel()).thenReturn(41);
        assertTrue("+9 total level satisfies first-level-10",
            applyAndGetCompleted().contains("achieve-your-first-level-10"));
    }

    @Test
    public void combatLevelRuleReadsThePlayer()
    {
        stats(1, 0, 9);
        assertFalse(applyAndGetCompleted().contains("reach-combat-level-10"));

        stats(1, 0, 10);
        assertTrue(applyAndGetCompleted().contains("reach-combat-level-10"));
    }

    @Test
    public void appliedTasksAreRecordedAsAutoNotManual()
    {
        stats(20, 0, 1);
        applyAndGetCompleted();

        CompletedTaskRecord record = store.allRecords().stream()
            .filter(r -> "level-20-attack".equals(r.getTaskId()))
            .findFirst()
            .orElse(null);
        assertNotNull(record);
        assertEquals(CompletedTaskRecord.Source.AUTO, record.getSource());
    }

    @Test
    public void previewDropsToZeroOnceApplied()
    {
        stats(20, 0, 1);
        int before = preview().getTotal();
        assertTrue(before > 0);

        applyAndGetCompleted();

        AutocompletePreview after = preview();
        assertEquals("everything detectable is now marked", 0, after.getTotal());
        assertTrue(after.getCategories().isEmpty());
    }

    @Test
    public void previewBreakdownCountsMatchTheTotal()
    {
        stats(50, 0, 60);
        AutocompletePreview preview = preview();

        int summed = preview.getCategories().stream().mapToInt(c -> c.getCount()).sum();
        assertEquals("breakdown must account for every pending task", preview.getTotal(), summed);
        assertNull(preview.getUnavailableReason());
    }

    @Test
    public void questRulesAreEvaluatedAndMarkWhenFinished()
    {
        stats(1, 0, 3);
        assertFalse("an unfinished quest must not be marked",
            applyAndGetCompleted().contains("complete-client-of-kourend"));

        when(client.getIntStack()).thenReturn(new int[]{QUEST_FINISHED});
        assertTrue("a finished quest is marked",
            applyAndGetCompleted().contains("complete-client-of-kourend"));
    }

    @Test
    public void loggedOutPreviewIsUnavailableAndApplyWritesNothing()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getLocalPlayer()).thenReturn(null);

        AutocompletePreview preview = preview();
        assertNotNull("logged out must explain itself, not show a bare zero",
            preview.getUnavailableReason());
        assertEquals(0, preview.getTotal());

        assertTrue("apply must be inert while logged out", applyAndGetCompleted().isEmpty());
    }

    @Test
    public void diaryTierRuleMarksWhenComplete()
    {
        AchievementDiaryCatalog diaryCatalog = mock(AchievementDiaryCatalog.class);
        when(diaryCatalog.isAvailable()).thenReturn(true);
        when(diaryCatalog.build(client)).thenReturn(Arrays.asList(
            new DiaryProgress("ARDOUGNE", "EASY", true),
            new DiaryProgress("ARDOUGNE", "MEDIUM", false)));

        detector = buildDetector(diaryCatalog, mock(CombatAchievementCatalog.class),
            mock(BossKillCatalog.class));
        stats(1, 0, 3);

        Set<String> completed = applyAndGetCompleted();
        assertTrue(completed.contains("complete-the-easy-ardougne-diary"));
        assertFalse(completed.contains("complete-the-medium-ardougne-diary"));
    }

    @Test
    public void combatAchievementPointsRuleMarksAtThreshold()
    {
        CombatAchievementCatalog combatCatalog = mock(CombatAchievementCatalog.class);
        when(combatCatalog.isAvailable()).thenReturn(true);
        when(combatCatalog.build(client)).thenReturn(new CombatAchievementSnapshot(
            99, Collections.emptyList()));

        detector = buildDetector(mock(AchievementDiaryCatalog.class), combatCatalog,
            mock(BossKillCatalog.class));
        stats(1, 0, 3);

        assertFalse(applyAndGetCompleted().contains("100-combat-achievement-points"));

        when(combatCatalog.build(client)).thenReturn(new CombatAchievementSnapshot(
            100, Collections.emptyList()));
        assertTrue(applyAndGetCompleted().contains("100-combat-achievement-points"));
    }

    @Test
    public void combatAchievementTierCountRuleUsesBundledIndex()
    {
        CombatAchievementCatalog combatCatalog = mock(CombatAchievementCatalog.class);
        when(combatCatalog.isAvailable()).thenReturn(true);
        List<CombatAchievementTask> tasks = Arrays.asList(
            new CombatAchievementTask("ABBERANT_SPECTRE_KILLCOUNT_1", true));
        when(combatCatalog.build(client)).thenReturn(new CombatAchievementSnapshot(0, tasks));

        detector = buildDetector(mock(AchievementDiaryCatalog.class), combatCatalog,
            mock(BossKillCatalog.class));
        stats(1, 0, 3);

        assertTrue(applyAndGetCompleted().contains("1-easy-combat-achievement"));
    }

    @Test
    public void combatAchievementGroupRuleRequiresEveryPrefixComplete()
    {
        CombatAchievementCatalog combatCatalog = mock(CombatAchievementCatalog.class);
        when(combatCatalog.isAvailable()).thenReturn(true);
        List<CombatAchievementTask> partial = Arrays.asList(
            new CombatAchievementTask("ABYSSALSIRE_KILLCOUNT_1", true),
            new CombatAchievementTask("ABYSSALSIRE_MECHANICAL_1", false));
        when(combatCatalog.build(client)).thenReturn(new CombatAchievementSnapshot(0, partial));

        detector = buildDetector(mock(AchievementDiaryCatalog.class), combatCatalog,
            mock(BossKillCatalog.class));
        stats(1, 0, 3);
        assertFalse(applyAndGetCompleted().contains("abyssal-sire-combat-achievements"));

        List<CombatAchievementTask> complete = Arrays.asList(
            new CombatAchievementTask("ABYSSALSIRE_KILLCOUNT_1", true),
            new CombatAchievementTask("ABYSSALSIRE_MECHANICAL_1", true),
            new CombatAchievementTask("ABYSSALSIRE_KILLCOUNT_2", true));
        when(combatCatalog.build(client)).thenReturn(new CombatAchievementSnapshot(0, complete));
        assertTrue(applyAndGetCompleted().contains("abyssal-sire-combat-achievements"));
    }

    @Test
    public void unavailableCatalogsLeaveDiaryAndCaRulesUnsatisfied()
    {
        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            mock(BossKillCatalog.class));
        stats(99, 200_000_000, 126);

        Set<String> completed = applyAndGetCompleted();
        assertFalse(completed.contains("complete-the-easy-ardougne-diary"));
        assertFalse(completed.contains("100-combat-achievement-points"));
        assertFalse(completed.contains("25-wintertodt-kills"));
        assertTrue(completed.contains("level-20-attack"));
    }

    @Test
    public void bossKillRuleMarksAtThreshold()
    {
        BossKillCatalog bossKillCatalog = mock(BossKillCatalog.class);
        when(bossKillCatalog.getKillCount(client, "TOTAL_WINTERTODT_KILLS")).thenReturn(24);

        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            bossKillCatalog);
        stats(1, 0, 3);

        assertFalse(applyAndGetCompleted().contains("25-wintertodt-kills"));

        when(bossKillCatalog.getKillCount(client, "TOTAL_WINTERTODT_KILLS")).thenReturn(25);
        assertTrue(applyAndGetCompleted().contains("25-wintertodt-kills"));
    }

    @Test
    public void bossKillOnceRuleMarksFirstGiantMoleKill()
    {
        BossKillCatalog bossKillCatalog = mock(BossKillCatalog.class);
        when(bossKillCatalog.getKillCount(client, "TOTAL_MOLE_KILLS")).thenReturn(0);

        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            bossKillCatalog);
        stats(1, 0, 3);

        assertFalse(applyAndGetCompleted().contains("defeat-the-giant-mole"));

        when(bossKillCatalog.getKillCount(client, "TOTAL_MOLE_KILLS")).thenReturn(1);
        assertTrue(applyAndGetCompleted().contains("defeat-the-giant-mole"));
        assertFalse(applyAndGetCompleted().contains("defeat-the-giant-mole-50-times"));
    }

    @Test
    public void bossKillAllRuleRequiresEveryVarp()
    {
        BossKillCatalog bossKillCatalog = mock(BossKillCatalog.class);
        when(bossKillCatalog.getKillCount(client, "TOTAL_PRIME_KILLS")).thenReturn(50);
        when(bossKillCatalog.getKillCount(client, "TOTAL_REX_KILLS")).thenReturn(50);
        when(bossKillCatalog.getKillCount(client, "TOTAL_SUPREME_KILLS")).thenReturn(49);

        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            bossKillCatalog);
        stats(1, 0, 3);
        assertFalse(applyAndGetCompleted().contains("defeat-each-dagannoth-king-50-times"));

        when(bossKillCatalog.getKillCount(client, "TOTAL_SUPREME_KILLS")).thenReturn(50);
        assertTrue(applyAndGetCompleted().contains("defeat-each-dagannoth-king-50-times"));
    }

    @Test
    public void hiscoresBossKillRuleMarksAtThreshold()
    {
        HiscoresKillCatalog hiscoresKillCatalog = mock(HiscoresKillCatalog.class);
        doAnswer(invocation ->
        {
            Consumer<HiscoresSnapshot> callback = invocation.getArgument(1);
            callback.accept(new HiscoresSnapshot(Collections.singletonMap("Skotizo", 25)));
            return null;
        }).when(hiscoresKillCatalog).prefetch(anyString(), any());

        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            mock(BossKillCatalog.class),
            hiscoresKillCatalog);
        stats(1, 0, 3);

        assertTrue(applyAndGetCompleted().contains("25-skotizo-kills"));
    }

    @Test
    public void clueCountRuleMarksSameTierTasksAtOrBelowThreshold()
    {
        ClueScrollCatalog clueScrollCatalog = mock(ClueScrollCatalog.class);
        when(clueScrollCatalog.completionsForTier(client, "HARD")).thenReturn(60);

        detector = buildDetector(
            mock(AchievementDiaryCatalog.class),
            mock(CombatAchievementCatalog.class),
            mock(BossKillCatalog.class),
            clueScrollCatalog,
            mockHiscores());
        stats(1, 0, 3);

        Set<String> completed = applyAndGetCompleted();
        assertTrue(completed.contains("1-hard-clue-scroll"));
        assertTrue(completed.contains("50-hard-clue-scrolls"));
        assertFalse(completed.contains("75-hard-clue-scrolls"));
        // A different tier reads its own counter (default mock: 0).
        assertFalse(completed.contains("50-elite-clue-scrolls"));
    }
}
