package com.leaguestasks.ui;

import com.leaguestasks.sync.AutocompletePreview;
import com.leaguestasks.sync.TaskAutoDetector;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AutocompleteCardTest
{
    private TaskAutoDetector detector;
    private AutocompleteCard card;
    private JButton autocompleteButton;
    private JButton cancelButton;
    private AutocompletePreview nextPreview;

    @Before
    public void setUp() throws Exception
    {
        detector = mock(TaskAutoDetector.class);
        doAnswer(i ->
        {
            Consumer<AutocompletePreview> callback = i.getArgument(0);
            callback.accept(nextPreview);
            return null;
        }).when(detector).preview(any());

        nextPreview = AutocompletePreview.of(3, Collections.singletonList(
            new AutocompletePreview.CategoryCount("Quests", 3)));
        onEdt(() ->
        {
            card = new AutocompleteCard(detector, mock(Navigator.class));
            autocompleteButton = button("Autocomplete Tasks");
            cancelButton = button("Cancel");
            card.onOpened();
        });
    }

    @Test
    public void firstClickOnlyArmsTheConfirm() throws Exception
    {
        onEdt(() -> autocompleteButton.doClick());

        assertEquals("Confirm: mark 3 complete", autocompleteButton.getText());
        assertTrue(cancelButton.isVisible());
        verify(detector, never()).apply(any());
    }

    @Test
    public void secondClickApplies() throws Exception
    {
        onEdt(() ->
        {
            autocompleteButton.doClick();
            autocompleteButton.doClick();
        });

        verify(detector).apply(any());
    }

    @Test
    public void cancelDisarmsWithoutApplying() throws Exception
    {
        onEdt(() ->
        {
            autocompleteButton.doClick();
            cancelButton.doClick();
        });

        assertEquals("Autocomplete Tasks", autocompleteButton.getText());
        assertFalse(cancelButton.isVisible());
        verify(detector, never()).apply(any());
    }

    @Test
    public void nothingPendingDisablesTheButton() throws Exception
    {
        nextPreview = AutocompletePreview.of(0, Collections.emptyList());
        onEdt(() -> card.refreshPreview());

        assertFalse(autocompleteButton.isEnabled());
    }

    @Test
    public void unavailablePreviewDisablesTheButton() throws Exception
    {
        nextPreview = AutocompletePreview.unavailable("Log in first.");
        onEdt(() -> card.refreshPreview());

        assertFalse(autocompleteButton.isEnabled());
    }

    private JButton button(String text)
    {
        for (Component c : card.getComponents())
        {
            if (c instanceof JButton && text.equals(((JButton) c).getText()))
            {
                return (JButton) c;
            }
        }
        throw new AssertionError("no button labelled " + text);
    }

    private static void onEdt(Runnable r) throws InterruptedException, InvocationTargetException
    {
        SwingUtilities.invokeAndWait(r);
    }
}
