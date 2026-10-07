package com.leaguestasks.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SyncUrlTest
{
    @Test
    public void httpsIsAcceptedAndNormalized()
    {
        assertEquals("https://tracker.example:4123", SyncUrl.parse("https://tracker.example:4123/"));
        assertEquals("https://tracker.example", SyncUrl.parse("  https://tracker.example///  "));
    }

    @Test
    public void localhostHttpIsAccepted()
    {
        assertEquals("http://localhost:4123", SyncUrl.parse("http://localhost:4123"));
        assertEquals("http://127.0.0.1:8090", SyncUrl.parse("http://127.0.0.1:8090"));
        assertEquals("http://box.localhost", SyncUrl.parse("http://box.localhost"));
    }

    @Test
    public void ipv6LoopbackHttpIsAccepted()
    {
        assertEquals("http://[::1]:4123", SyncUrl.parse("http://[::1]:4123"));
    }

    @Test
    public void isLoopbackEdgeCases()
    {
        assertTrue(SyncUrl.isLoopback("LOCALHOST"));
        assertTrue(SyncUrl.isLoopback("::1"));
        assertTrue(SyncUrl.isLoopback("0:0:0:0:0:0:0:1"));
        assertFalse(SyncUrl.isLoopback("localhost.attacker.example"));
        assertFalse(SyncUrl.isLoopback("notlocalhost"));
        assertFalse(SyncUrl.isLoopback(""));
        assertFalse(SyncUrl.isLoopback(null));
    }

    @Test
    public void nonLocalhostHttpIsRejectedRatherThanSendTheTokenInCleartext()
    {
        assertNull(SyncUrl.parse("http://192.168.1.10:4123"));
        assertNull(SyncUrl.parse("http://tracker.example"));
        assertNull(SyncUrl.parse("http://localhost.attacker.example"));
    }

    @Test
    public void nonHttpSchemesAndJunkAreRejected()
    {
        assertNull(SyncUrl.parse("file:///tmp/tracker"));
        assertNull(SyncUrl.parse("ftp://example.com"));
        assertNull(SyncUrl.parse("javascript:alert(1)"));
        assertNull(SyncUrl.parse("not a url"));
        assertNull(SyncUrl.parse(""));
        assertNull(SyncUrl.parse(null));
    }
}
