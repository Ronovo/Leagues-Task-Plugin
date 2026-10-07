package com.leaguestasks.ui;

import java.util.Locale;

public final class TextUtil
{
    /** Non-breaking space, as the game returns it inside display names. */
    private static final char NBSP = '\u00A0';

    private TextUtil()
    {
    }

    /**
     * Canonical display name for sync payloads and server storage keys:
     * NBSP folded to space, trimmed, lowercased. Matches the tracker's
     * {@code storageKey()} rules (spaces, not underscores).
     */
    public static String normalizeDisplayName(String displayName)
    {
        if (isBlank(displayName))
        {
            return null;
        }

        return displayName.replace(NBSP, ' ').trim().toLowerCase(Locale.ENGLISH);
    }

    /**
     * Config-key form of a display name: same folding as
     * {@link #normalizeDisplayName}, then spaces become underscores so the
     * value survives as a RuneLite config key suffix.
     */
    public static String normalizeAccountKey(String displayName)
    {
        String normalized = normalizeDisplayName(displayName);
        if (normalized == null)
        {
            return null;
        }

        return normalized.replace(' ', '_');
    }

    public static String capitalize(String s)
    {
        if (s == null || s.isEmpty())
        {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1);
    }

    public static boolean isBlank(String s)
    {
        return s == null || s.trim().isEmpty();
    }

    public static String escapeHtml(String s)
    {
        if (s == null || s.isEmpty())
        {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
