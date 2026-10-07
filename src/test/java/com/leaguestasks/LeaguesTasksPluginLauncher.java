package com.leaguestasks;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev-only entry point (not shipped in the plugin jar) that launches a local
 * RuneLite client with this plugin sideloaded. Run via `./gradlew run`.
 */
public class LeaguesTasksPluginLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(LeaguesTasksPlugin.class);
        RuneLite.main(args);
    }
}
