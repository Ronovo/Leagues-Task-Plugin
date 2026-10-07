package com.leaguestasks.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A hostile tracker's error body is logged for diagnosis; it must not be
 * able to smuggle newlines or terminal escapes into the client log, and it
 * must be bounded in length.
 */
public class ProgressSyncClientLogSanitizeTest
{
    private static final String ESC = "";

    @Test
    public void controlCharactersBecomeSpaces()
    {
        String sanitized = ProgressSyncClient.sanitizeForLog("line one\r\nline two\t" + ESC + "[31mred");

        assertFalse(sanitized.contains("\n"));
        assertFalse(sanitized.contains("\r"));
        assertFalse(sanitized.contains("\t"));
        assertFalse(sanitized.contains(ESC));
        assertTrue(sanitized.contains("line one"));
        assertTrue(sanitized.contains("line two"));
    }

    @Test
    public void longBodiesAreTruncated()
    {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 5000; i++)
        {
            body.append('A');
        }

        String sanitized = ProgressSyncClient.sanitizeForLog(body.toString());

        assertTrue(sanitized.length() < 600);
        assertTrue(sanitized.endsWith("... (truncated)"));
    }

    @Test
    public void shortCleanBodiesPassThrough()
    {
        assertEquals("validation: field X missing", ProgressSyncClient.sanitizeForLog("validation: field X missing"));
    }

    @Test
    public void nullBecomesAPlaceholder()
    {
        assertEquals("(no body)", ProgressSyncClient.sanitizeForLog(null));
    }
}
