package com.leaguestasks.sync;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.SpriteID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/**
 * Adds a "Sync" button to the top-right of the collection log interface.
 *
 * <p>Syncing a collection log means running its search while the interface
 * is open (see {@link CollectionLogCapture}), so putting the button on the
 * interface itself saves switching to the sidebar at exactly the moment the
 * log has to stay open. Collection log sync is in-game only; there is no
 * sidebar "Sync Collection Log" button.
 *
 * <p>Modelled on WikiSync's collection log button: nine sprite widgets for
 * the border and one text widget on top carrying the listeners, since the
 * text covers the whole area.
 */
@Singleton
public class CollectionLogSyncButton
{
    /** Rebuilds the collection log interface; the button goes with it. */
    private static final int COLLECTION_LOG_SETUP_SCRIPT = 7797;

    private static final int[] SPRITES_IDLE = {
        SpriteID.DIALOG_BACKGROUND,
        SpriteID.WORLD_MAP_BUTTON_METAL_CORNER_TOP_LEFT,
        SpriteID.WORLD_MAP_BUTTON_METAL_CORNER_TOP_RIGHT,
        SpriteID.WORLD_MAP_BUTTON_METAL_CORNER_BOTTOM_LEFT,
        SpriteID.WORLD_MAP_BUTTON_METAL_CORNER_BOTTOM_RIGHT,
        SpriteID.WORLD_MAP_BUTTON_EDGE_LEFT,
        SpriteID.WORLD_MAP_BUTTON_EDGE_TOP,
        SpriteID.WORLD_MAP_BUTTON_EDGE_RIGHT,
        SpriteID.WORLD_MAP_BUTTON_EDGE_BOTTOM,
    };

    private static final int[] SPRITES_HOVERED = {
        SpriteID.RESIZEABLE_MODE_SIDE_PANEL_BACKGROUND,
        SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_LEFT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_TOP_RIGHT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_LEFT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_METAL_CORNER_BOTTOM_RIGHT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_EDGE_LEFT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_EDGE_TOP_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_EDGE_RIGHT_HOVERED,
        SpriteID.EQUIPMENT_BUTTON_EDGE_BOTTOM_HOVERED,
    };

    private static final String BUTTON_TEXT = "Sync";
    private static final int COLOUR_IDLE = 0xd6d6d6;
    private static final int COLOUR_HOVERED = 0xffffff;
    // Layout mirrors the collection log close button on interface 875.
    private static final int CLOSE_BUTTON_OFFSET = 28;
    private static final int BUTTON_WIDTH = 45;
    private static final int GAP_TO_CLOSE_BUTTON = 5;
    private static final int BUTTON_OFFSET = CLOSE_BUTTON_OFFSET + GAP_TO_CLOSE_BUTTON;
    private static final int CORNER = 9;
    // The side edge sprites tile vertically, so a nominal height is enough (as WikiSync does).
    private static final int SIDE_EDGE_HEIGHT = 4;
    // Overlap so the top/bottom edges meet the corners without a seam.
    private static final int EDGE_OVERLAP = 1;

    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final ProgressSyncClient progressSyncClient;

    private Widget[] injectedSprites;
    private Widget injectedText;
    private int savedTopBarWidth = -1;

    @Inject
    public CollectionLogSyncButton(Client client, ClientThread clientThread, EventBus eventBus,
        ProgressSyncClient progressSyncClient)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.progressSyncClient = progressSyncClient;
    }

    public void startUp()
    {
        eventBus.register(this);
        // The log may already be open when the plugin is toggled on.
        clientThread.invokeLater(this::addButton);
    }

    public void shutDown()
    {
        eventBus.unregister(this);
        clientThread.invokeLater(this::removeButton);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT)
        {
            removeButton();
            addButton();
        }
    }

    private void onClicked()
    {
        client.addChatMessage(ChatMessageType.CONSOLE, "Leagues Tasks",
            "Syncing your collection log...", "Leagues Tasks");
        progressSyncClient.syncCollectionLog((outcome, message) ->
            clientThread.invokeLater(() ->
                client.addChatMessage(ChatMessageType.CONSOLE, "Leagues Tasks", message, "Leagues Tasks")));
    }

    private void addButton()
    {
        Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
        Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
        Widget topBar = findTopBar();
        if (parent == null || searchButton == null || topBar == null)
        {
            return;
        }

        final int w = BUTTON_WIDTH;
        final int h = searchButton.getOriginalHeight();
        final int x = BUTTON_OFFSET;
        final int y = searchButton.getOriginalY();

        final Widget[] sprites = new Widget[SPRITES_IDLE.length];

        sprites[0] = parent.createChild(-1, WidgetType.GRAPHIC)
            .setSpriteId(SPRITES_IDLE[0])
            .setPos(x, y)
            .setSize(w, h)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setYPositionMode(searchButton.getYPositionMode());

        sprites[1] = corner(parent, SPRITES_IDLE[1], x + (w - CORNER), y);
        sprites[2] = corner(parent, SPRITES_IDLE[2], x, y);
        sprites[3] = corner(parent, SPRITES_IDLE[3], x + (w - CORNER), y + h - CORNER);
        sprites[4] = corner(parent, SPRITES_IDLE[4], x, y + h - CORNER);

        sprites[5] = edge(parent, SPRITES_IDLE[5], CORNER, SIDE_EDGE_HEIGHT, x + (w - CORNER), y + CORNER);
        sprites[7] = edge(parent, SPRITES_IDLE[7], CORNER, SIDE_EDGE_HEIGHT, x, y + CORNER);

        final int topWidth = w - (CORNER * 2) + EDGE_OVERLAP;
        sprites[6] = edge(parent, SPRITES_IDLE[6], topWidth, CORNER, x + CORNER, y);
        sprites[8] = edge(parent, SPRITES_IDLE[8], topWidth, CORNER, x + CORNER, y + h - CORNER);

        for (Widget sprite : sprites)
        {
            sprite.revalidate();
        }

        final Widget text = parent.createChild(-1, WidgetType.TEXT)
            .setText(BUTTON_TEXT)
            .setTextColor(COLOUR_IDLE)
            .setFontId(FontID.PLAIN_11)
            .setTextShadowed(true)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setXTextAlignment(WidgetTextAlignment.CENTER)
            .setYTextAlignment(WidgetTextAlignment.CENTER)
            .setPos(x, y)
            .setSize(w, h)
            .setYPositionMode(searchButton.getYPositionMode());
        text.revalidate();

        // The text layer covers the whole button, so it owns the listeners.
        text.setHasListener(true);
        text.setOnMouseOverListener((JavaScriptCallback) ev -> paint(sprites, text, SPRITES_HOVERED, COLOUR_HOVERED));
        text.setOnMouseLeaveListener((JavaScriptCallback) ev -> paint(sprites, text, SPRITES_IDLE, COLOUR_IDLE));
        text.setAction(0, "Sync your progress and collection log");
        text.setOnOpListener((JavaScriptCallback) ev -> onClicked());

        injectedSprites = sprites;
        injectedText = text;

        // Keep the drag handle from running underneath the new button. Only
        // narrow once per add/remove cycle -- if a stray add happens without
        // a preceding remove, savedTopBarWidth is already set and narrowing
        // again would compound the shrink.
        if (savedTopBarWidth < 0)
        {
            savedTopBarWidth = topBar.getOriginalWidth();
            topBar.setOriginalWidth(savedTopBarWidth - (w + (x - CLOSE_BUTTON_OFFSET)));
            topBar.revalidate();
        }

        parent.revalidate();
    }

    /**
     * Undoes exactly what {@link #addButton()} did: the interface is shared
     * with other plugins (WikiSync injects its own button into the same
     * {@code Collection.UNIVERSE} widget), so this can't call
     * {@code deleteAllChildren()} -- that would delete their widgets too.
     * Instead it nulls out only the sparse-array slots this class's own
     * widgets occupy, leaving every other slot's index untouched, and
     * restores the top bar width captured in {@link #addButton()}.
     */
    private void removeButton()
    {
        Widget parent = client.getWidget(InterfaceID.Collection.UNIVERSE);
        if (parent != null && (injectedSprites != null || injectedText != null))
        {
            Widget[] children = parent.getChildren();
            if (children != null)
            {
                boolean changed = false;
                for (int i = 0; i < children.length; i++)
                {
                    Widget child = children[i];
                    if (child != null && (child == injectedText || containsIdentity(injectedSprites, child)))
                    {
                        children[i] = null;
                        changed = true;
                    }
                }
                if (changed)
                {
                    parent.setChildren(children);
                }
            }
            parent.revalidate();
        }
        injectedSprites = null;
        injectedText = null;

        if (savedTopBarWidth >= 0)
        {
            Widget topBar = findTopBar();
            if (topBar != null)
            {
                topBar.setOriginalWidth(savedTopBarWidth);
                topBar.revalidate();
            }
            savedTopBarWidth = -1;
        }
    }

    /** The collection log's draggable title bar, or null while the interface is closed. */
    private Widget findTopBar()
    {
        Widget container = client.getWidget(InterfaceID.Collection.INFINITY);
        if (container == null)
        {
            return null;
        }
        Widget[] children = container.getChildren();
        return children != null && children.length > 0 ? children[0] : null;
    }

    private static boolean containsIdentity(Widget[] widgets, Widget target)
    {
        if (widgets == null)
        {
            return false;
        }
        for (Widget w : widgets)
        {
            if (w == target)
            {
                return true;
            }
        }
        return false;
    }

    private Widget corner(Widget parent, int spriteId, int x, int y)
    {
        return edge(parent, spriteId, CORNER, CORNER, x, y);
    }

    private Widget edge(Widget parent, int spriteId, int width, int height, int x, int y)
    {
        return parent.createChild(-1, WidgetType.GRAPHIC)
            .setSpriteId(spriteId)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setSize(width, height)
            .setPos(x, y);
    }

    private static void paint(Widget[] sprites, Widget text, int[] spriteIds, int textColour)
    {
        for (int i = 0; i < sprites.length; i++)
        {
            sprites[i].setSpriteId(spriteIds[i]);
        }
        text.setTextColor(textColour);
    }
}
