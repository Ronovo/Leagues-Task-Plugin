package com.leaguestasks.sync;

import java.util.Locale;
import okhttp3.HttpUrl;

/**
 * Validates the tracker base URL before any Bearer token is sent, and
 * normalizes it (trailing slashes trimmed).
 *
 * <p>Only {@code http} and {@code https} are accepted, and plain {@code http}
 * is accepted <em>only</em> for a loopback host -- a non-loopback
 * {@code http://} URL is rejected outright rather than send the token in
 * cleartext over the network. {@link #parse} returns {@code null} for
 * anything it rejects.
 */
final class SyncUrl
{
    private SyncUrl()
    {
    }

    /**
     * @return the normalized base URL (no trailing slash), or null if the
     * input is blank, unparseable, not http/https, or a non-loopback
     * {@code http://} address.
     */
    static String parse(String serverUrl)
    {
        if (serverUrl == null)
        {
            return null;
        }

        String trimmed = serverUrl.trim();
        HttpUrl url = HttpUrl.parse(trimmed);
        if (url == null)
        {
            return null;
        }

        String scheme = url.scheme();
        if (!"http".equals(scheme) && !"https".equals(scheme))
        {
            return null;
        }

        if ("http".equals(scheme) && !isLoopback(url.host()))
        {
            return null;
        }

        String base = trimmed;
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    static boolean isLoopback(String host)
    {
        if (host == null)
        {
            return false;
        }
        String h = host.toLowerCase(Locale.ENGLISH);
        return "localhost".equals(h)
            || "127.0.0.1".equals(h)
            || "0:0:0:0:0:0:0:1".equals(h)
            || "::1".equals(h)
            || h.endsWith(".localhost");
    }
}
