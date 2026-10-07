package com.leaguestasks.model;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.leaguestasks.ConfigKeys;
import com.leaguestasks.InMemoryConfig;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AccountScopedConfigTest
{
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>()
    {
    }.getType();
    private static final String PREFIX = ConfigKeys.COMPLETED_TASKS_PREFIX;

    private Map<String, String> config;
    private AccountScopedConfig scope;
    private final Gson gson = new Gson();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp()
    {
        config = new HashMap<>();
        ConfigManager configManager = InMemoryConfig.backedBy(config);

        scope = new AccountScopedConfig(configManager, gson);

        logger = (Logger) LoggerFactory.getLogger(AccountScopedConfig.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void tearDown()
    {
        logger.detachAppender(appender);
    }

    @Test
    public void setAccountReportsChangeAndWritesLastAccount()
    {
        assertTrue(scope.setAccount("Zezima"));
        assertEquals("zezima", config.get(ConfigKeys.LAST_ACCOUNT));

        assertFalse(scope.setAccount("Zezima"));
        assertFalse(scope.setAccount(null));
        assertFalse(scope.setAccount("  "));
    }

    @Test
    public void accountKeyIsNormalized()
    {
        scope.setAccount("Some\u00A0Alt");
        assertEquals("some_alt", scope.account());
        assertEquals(PREFIX + "some_alt", scope.storageKey(PREFIX));
    }

    @Test
    public void storageKeyFallsBackToLastAccountThenNull()
    {
        assertNull(scope.storageKey(PREFIX));

        config.put(ConfigKeys.LAST_ACCOUNT, "olduser");
        assertEquals(PREFIX + "olduser", scope.storageKey(PREFIX));

        scope.setAccount("Current");
        assertEquals(PREFIX + "current", scope.storageKey(PREFIX));
    }

    @Test
    public void unreadableJsonLogsAWarningNamingTheKeyAndReturnsFallback()
    {
        scope.setAccount("user");
        String key = scope.storageKey(PREFIX);
        config.put(key, "{not valid json");

        Map<String, String> result = scope.read(key, MAP_TYPE, HashMap::new);

        assertTrue(result.isEmpty());

        List<ILoggingEvent> warnings = appender.list;
        assertEquals(1, warnings.size());
        assertEquals(Level.WARN, warnings.get(0).getLevel());
        String message = warnings.get(0).getFormattedMessage();
        assertTrue(message.contains(key));
        assertFalse(message.contains("not valid json"));
    }

    @Test
    public void blankOrMissingJsonReturnsFallbackWithoutWarning()
    {
        assertTrue(scope.<Map<String, String>>read(null, MAP_TYPE, HashMap::new).isEmpty());
        assertTrue(appender.list.isEmpty());
    }
}
