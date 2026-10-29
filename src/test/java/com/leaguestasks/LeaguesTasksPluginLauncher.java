package com.leaguestasks;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Dev-only entry point (not shipped in the plugin jar) that launches a local
 * RuneLite client with this plugin sideloaded. Run via `./gradlew run`, or the
 * run-both task to load the other plugin as well.
 */
public class LeaguesTasksPluginLauncher
{
    public static void main(String[] args) throws Exception
    {
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        plugins.add(LeaguesTasksPlugin.class);
        // Set by the run-both Gradle task to the other plugin's class name.
        for (String name : System.getProperty("extraPlugins", "").split(","))
        {
            if (!name.isBlank())
            {
                plugins.add(Class.forName(name.trim()).asSubclass(Plugin.class));
            }
        }
        ExternalPluginManager.loadBuiltin(plugins.toArray(new Class[0]));
        RuneLite.main(args);
    }
}
