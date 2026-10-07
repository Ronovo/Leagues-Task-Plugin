package com.leaguestasks.ui;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InfoCardTest
{
    @Test
    public void firstSingleLineBlockIsACenteredBoldTitle()
    {
        String html = InfoCard.renderHtml(Arrays.asList(
            "About Plugin",
            "",
            "Some body text."));

        assertTrue(html, html.contains("text-align:center"));
        assertTrue(html, html.contains("color:" + SwingComponents.hex(SwingComponents.ACCENT_COLOR)));
        assertTrue(html, html.contains("<b>About Plugin</b></div>"));
        // Body text stays a plain left block.
        assertTrue(html, html.contains("<div style='margin:0 0 10px 0'>Some body text.</div>"));
    }

    @Test
    public void wrappedParagraphLinesReflowIntoOneBlock()
    {
        String html = InfoCard.renderHtml(Arrays.asList(
            "Title",
            "",
            "This plugin brings the Leagues task format",
            "to the main game."));

        assertTrue(html, html.contains("Leagues task format to the main game."));
        assertFalse("no hard break mid-paragraph", html.contains("<br>"));
        // Title div + one body div.
        assertEquals(2, countOccurrences(html, "<div"));
    }

    @Test
    public void blankLineStartsANewBodyBlock()
    {
        String html = InfoCard.renderHtml(Arrays.asList(
            "Title",
            "",
            "First paragraph.",
            "",
            "Second paragraph."));

        assertEquals(3, countOccurrences(html, "<div"));
        assertTrue(html, html.contains("First paragraph.</div>"));
        assertTrue(html, html.contains("Second paragraph.</div>"));
    }

    @Test
    public void dashLinesBecomeABulletList()
    {
        String html = InfoCard.renderHtml(Arrays.asList(
            "Works on any account type:",
            "- Existing main accounts",
            "- Ironmen of all flavors"));

        assertTrue(html, html.contains("<ul"));
        assertEquals(2, countOccurrences(html, "<li"));
        assertTrue(html, html.contains(">Existing main accounts</li>"));
        assertFalse("dash marker is stripped", html.contains("- Existing"));
    }

    @Test
    public void labeledLinkInATextBlockBecomesAnAnchor()
    {
        String html = InfoCard.renderHtml(Arrays.asList(
            "Get it from GitHub:",
            "",
            "[Tracker on GitHub](https://github.com/Ronovo/Personal-RuneScape-Tracker)"));

        assertTrue(html, html.contains("<a href=\"https://github.com/Ronovo/Personal-RuneScape-Tracker\""));
        assertTrue(html, html.contains(">Tracker on GitHub</a>"));
    }

    private static int countOccurrences(String haystack, String needle)
    {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1))
        {
            count++;
        }
        return count;
    }

    @Test
    public void loadsAndRendersABundledResource()
    {
        String html = InfoCard.loadContentHtml("/com/leaguestasks/welcome.txt");

        assertTrue(html, html.contains("Welcome to Leagues Tasks."));
    }

    @Test
    public void missingResourceRendersANoticeInsteadOfThrowing()
    {
        String html = InfoCard.loadContentHtml("/com/leaguestasks/no-such-file.txt");

        assertTrue(html, html.contains("not found"));
    }
}
