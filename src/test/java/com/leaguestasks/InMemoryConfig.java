package com.leaguestasks;

import java.util.Map;
import net.runelite.client.config.ConfigManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A {@link ConfigManager} mock backed by {@code values}, so code under test
 * runs its real read/write path against the plugin's config group.
 */
public final class InMemoryConfig
{
    private InMemoryConfig()
    {
    }

    public static ConfigManager backedBy(Map<String, String> values)
    {
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfiguration(eq(ConfigKeys.GROUP), anyString()))
            .thenAnswer(i -> values.get(i.getArgument(1, String.class)));
        doAnswer(i ->
        {
            // Bound to Object so String.valueOf() can't pick its char[] overload.
            Object value = i.getArgument(2, Object.class);
            values.put(i.getArgument(1, String.class), value == null ? null : value.toString());
            return null;
        }).when(configManager).setConfiguration(eq(ConfigKeys.GROUP), anyString(), any());
        return configManager;
    }
}
