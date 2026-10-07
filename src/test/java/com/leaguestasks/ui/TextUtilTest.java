package com.leaguestasks.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextUtilTest
{
    @Test
    public void capitalize()
    {
        assertEquals("Easy", TextUtil.capitalize("easy"));
        assertEquals("", TextUtil.capitalize(""));
        assertEquals(null, TextUtil.capitalize(null));
    }

    @Test
    public void isBlank()
    {
        assertTrue(TextUtil.isBlank(null));
        assertTrue(TextUtil.isBlank(""));
        assertTrue(TextUtil.isBlank(" \t\n"));
        assertFalse(TextUtil.isBlank(" token "));
    }

    @Test
    public void escapeHtml()
    {
        assertEquals("Romeo &amp; Juliet", TextUtil.escapeHtml("Romeo & Juliet"));
        assertEquals("&lt;b&gt;", TextUtil.escapeHtml("<b>"));
        assertEquals("", TextUtil.escapeHtml(null));
    }

    @Test
    public void normalizeDisplayName()
    {
        assertEquals("zezima 2", TextUtil.normalizeDisplayName("Zezima\u00A02"));
        assertEquals("some alt", TextUtil.normalizeDisplayName("Some\u00A0Alt"));
        assertEquals(null, TextUtil.normalizeDisplayName(null));
    }

    @Test
    public void normalizeAccountKey()
    {
        assertEquals("some_alt", TextUtil.normalizeAccountKey("Some Alt"));
        assertEquals("zezima", TextUtil.normalizeAccountKey("Zezima"));
    }
}
