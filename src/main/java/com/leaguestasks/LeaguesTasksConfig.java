package com.leaguestasks;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * User-facing settings: the shared connection to the tracker website.
 *
 * <p>Everything else this plugin persists is internal app state (saved and
 * completed tasks, the cached collection log) and goes through raw
 * {@code ConfigManager} get/setConfiguration instead -- see
 * {@link com.leaguestasks.model.SavedTaskStore} for why.
 */
@ConfigGroup(ConfigKeys.GROUP)
public interface LeaguesTasksConfig extends Config
{
    @ConfigSection(
        name = "Tracker website",
        description = "Connection used to sync your progress.",
        position = 0
    )
    String CONNECTION = "connection";

    @ConfigItem(
        keyName = ConfigKeys.SERVER_URL,
        name = "Server URL",
        description = "Base URL of your tracker, e.g. https://tracker.example or http://localhost:4123. Plain http:// is accepted only for localhost; a non-localhost http:// URL is refused so the token is never sent in cleartext.",
        section = CONNECTION,
        position = 0
    )
    default String serverUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = ConfigKeys.API_TOKEN,
        name = "API Token",
        description = "Personal API token for this tracker. On a LAN server use the shared SYNC_TOKEN; on a public site copy your personal token from the account page after signing in. Sent as a Bearer token; leave blank to disable syncing.",
        secret = true,
        section = CONNECTION,
        position = 1
    )
    default String apiToken()
    {
        return "";
    }
}
