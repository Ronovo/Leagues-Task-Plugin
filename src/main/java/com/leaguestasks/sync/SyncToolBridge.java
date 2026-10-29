package com.leaguestasks.sync;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * The link to Ronovo's Sync Tool, the separate plugin that sends progress to
 * the tracker. Plugin Hub plugins can't call each other's code, so this finds
 * it by its descriptor name and asks it for a tasks sync through a
 * {@link PluginMessage}. The Sync Tool reads the completions straight from
 * this plugin's saved config.
 */
@Singleton
public class SyncToolBridge
{
    private static final String SYNC_TOOL_NAME = "Ronovo's Sync Tool";
    // Must match RonovosSyncPlugin.MESSAGE_NAMESPACE / MESSAGE_SYNC_TASKS.
    private static final String MESSAGE_NAMESPACE = "ronovossync";
    private static final String MESSAGE_SYNC_TASKS = "syncTasks";

    private final PluginManager pluginManager;
    private final EventBus eventBus;

    @Inject
    public SyncToolBridge(PluginManager pluginManager, EventBus eventBus)
    {
        this.pluginManager = pluginManager;
        this.eventBus = eventBus;
    }

    /** True when the Sync Tool is installed and turned on. */
    public boolean isSyncToolActive()
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null && SYNC_TOOL_NAME.equals(descriptor.name()) && pluginManager.isPluginActive(plugin))
            {
                return true;
            }
        }
        return false;
    }

    /** The Sync Tool reports the result in the chatbox. */
    public void requestTaskSync()
    {
        eventBus.post(new PluginMessage(MESSAGE_NAMESPACE, MESSAGE_SYNC_TASKS));
    }
}
