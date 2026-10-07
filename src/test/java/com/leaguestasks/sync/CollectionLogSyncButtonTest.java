package com.leaguestasks.sync;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectionLogSyncButtonTest
{
    private static final int SETUP_SCRIPT = 7797;
    private static final int TOP_BAR_WIDTH = 200;

    private Client client;
    private ClientThread clientThread;
    private EventBus eventBus;
    private ProgressSyncClient progressSyncClient;
    private CollectionLogSyncButton button;

    private Widget parent;
    private Widget topBar;
    private Widget[] createdChildren;
    private int nextChild;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        eventBus = mock(EventBus.class);
        progressSyncClient = mock(ProgressSyncClient.class);
        button = new CollectionLogSyncButton(client, clientThread, eventBus, progressSyncClient);

        doAnswer(i ->
        {
            i.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        parent = mock(Widget.class);
        Widget searchButton = mock(Widget.class);
        Widget container = mock(Widget.class);
        topBar = mock(Widget.class);

        when(client.getWidget(InterfaceID.Collection.UNIVERSE)).thenReturn(parent);
        when(client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE)).thenReturn(searchButton);
        when(client.getWidget(InterfaceID.Collection.INFINITY)).thenReturn(container);
        when(container.getChildren()).thenReturn(new Widget[] {topBar});
        when(topBar.getOriginalWidth()).thenReturn(TOP_BAR_WIDTH);

        // Every sprite and the text layer are created via parent.createChild;
        // hand back a fresh RETURNS_SELF mock per call so the fluent setter
        // chains work and each widget has its own identity.
        createdChildren = new Widget[10];
        for (int i = 0; i < createdChildren.length; i++)
        {
            createdChildren[i] = mock(Widget.class, RETURNS_SELF);
        }
        nextChild = 0;
        when(parent.createChild(anyInt(), anyInt())).thenAnswer(i -> createdChildren[nextChild++]);
    }

    private static ScriptPostFired scriptEvent(int id)
    {
        return new ScriptPostFired(id);
    }

    @Test
    public void startUpRegistersAndSchedulesAnAdd()
    {
        button.startUp();

        verify(eventBus).register(button);
        verify(clientThread).invokeLater(any(Runnable.class));
        verify(parent, atLeastOnce()).createChild(anyInt(), anyInt());
    }

    @Test
    public void setupScriptReinjectsTheButton()
    {
        button.onScriptPostFired(scriptEvent(SETUP_SCRIPT));

        // 9 border sprites + 1 text layer.
        verify(parent, org.mockito.Mockito.times(10)).createChild(anyInt(), anyInt());
        verify(topBar).setOriginalWidth(TOP_BAR_WIDTH - 50);
        verify(parent, atLeastOnce()).revalidate();
    }

    @Test
    public void otherScriptsAreIgnored()
    {
        button.onScriptPostFired(scriptEvent(1234));

        verify(parent, never()).createChild(anyInt(), anyInt());
    }

    @Test
    public void removeButtonNullsOnlyItsOwnSlotsAndLeavesSiblingsIntact()
    {
        button.onScriptPostFired(scriptEvent(SETUP_SCRIPT));

        Widget wikiSyncButton = mock(Widget.class);
        Widget[] children = new Widget[createdChildren.length + 1];
        children[0] = wikiSyncButton;
        System.arraycopy(createdChildren, 0, children, 1, createdChildren.length);
        when(parent.getChildren()).thenReturn(children);

        button.shutDown();

        assertSame("a sibling plugin's widget must survive", wikiSyncButton, children[0]);
        for (int i = 1; i < children.length; i++)
        {
            assertNull("our widget slot " + i + " should be cleared", children[i]);
        }
        verify(parent).setChildren(children);
        verify(topBar).setOriginalWidth(TOP_BAR_WIDTH);
    }

    @Test
    public void clickSyncsTheCollectionLogAndReportsTheResult()
    {
        button.onScriptPostFired(scriptEvent(SETUP_SCRIPT));

        Widget text = createdChildren[9];
        ArgumentCaptor<Object> listener = ArgumentCaptor.forClass(Object.class);
        verify(text).setOnOpListener(listener.capture());

        // the production lambda ignores the event argument
        ((JavaScriptCallback) listener.getValue()).run(null);

        verify(client).addChatMessage(eq(ChatMessageType.CONSOLE), eq("Leagues Tasks"),
            eq("Syncing your collection log..."), eq("Leagues Tasks"));

        ArgumentCaptor<SyncCallback> callback = ArgumentCaptor.forClass(SyncCallback.class);
        verify(progressSyncClient).syncCollectionLog(callback.capture());
        assertNotNull(callback.getValue());

        callback.getValue().onResult(SyncOutcome.SUCCESS, "Synced 5 collection log item(s) as Zezima.");
        verify(client).addChatMessage(eq(ChatMessageType.CONSOLE), eq("Leagues Tasks"),
            eq("Synced 5 collection log item(s) as Zezima."), eq("Leagues Tasks"));
    }
}
