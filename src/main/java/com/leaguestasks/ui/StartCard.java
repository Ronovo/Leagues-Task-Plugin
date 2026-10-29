package com.leaguestasks.ui;

import com.leaguestasks.model.TaskRepository;
import com.leaguestasks.sync.SyncToolBridge;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JSeparator;

/**
 * Primary menu card routing to the list, browse, picker, and info cards. The
 * sync button asks Ronovo's Sync Tool for a tasks sync when it is turned on,
 * and otherwise opens the Tracker App card, which explains installing it.
 */
public class StartCard extends MenuCard
{
    private static final String SYNC_LABEL = "Sync Progress";
    private static final String INSTALL_LABEL = "Install Ronovo's Sync Tool";

    private final SyncToolBridge syncToolBridge;
    private final JButton syncButton;

    public StartCard(Navigator navigator, SyncToolBridge syncToolBridge)
    {
        this.syncToolBridge = syncToolBridge;

        JButton showListButton = SwingComponents.centeredButton("Show My List");
        showListButton.addActionListener(e -> navigator.showList());

        JButton checkFullListButton = SwingComponents.centeredButton("Check Full List");
        checkFullListButton.addActionListener(e -> navigator.showBrowse());

        JButton pickButton = SwingComponents.centeredButton("Pick Random Task");
        pickButton.addActionListener(e -> navigator.showPicker(TaskRepository.ANY));

        syncButton = SwingComponents.centeredButton(SYNC_LABEL);
        syncButton.addActionListener(e ->
        {
            if (syncToolBridge.isSyncToolActive())
            {
                syncToolBridge.requestTaskSync();
            }
            else
            {
                navigator.showCard(Navigator.CARD_TRACKER);
            }
            refreshSyncButton();
        });
        refreshSyncButton();

        JButton autocompleteButton = SwingComponents.centeredButton("Autocomplete Tasks");
        autocompleteButton.addActionListener(e -> navigator.showAutocomplete());

        JSeparator topSeparator = new JSeparator();
        topSeparator.setMaximumSize(new Dimension(Integer.MAX_VALUE, topSeparator.getPreferredSize().height));

        JSeparator syncSeparator = new JSeparator();
        syncSeparator.setMaximumSize(new Dimension(Integer.MAX_VALUE, syncSeparator.getPreferredSize().height));

        JButton welcomeButton = SwingComponents.centeredButton("Welcome");
        welcomeButton.addActionListener(e -> navigator.showCard(Navigator.CARD_WELCOME));

        JButton aboutButton = SwingComponents.centeredButton("About Plugin");
        aboutButton.addActionListener(e -> navigator.showCard(Navigator.CARD_ABOUT));

        JButton trackerButton = SwingComponents.centeredButton("Tracker App");
        trackerButton.addActionListener(e -> navigator.showCard(Navigator.CARD_TRACKER));

        SwingComponents.colorButton(welcomeButton, SwingComponents.WELCOME_COLOR);
        SwingComponents.colorButton(aboutButton, SwingComponents.ABOUT_COLOR);
        SwingComponents.colorButton(trackerButton, SwingComponents.TRACKER_COLOR);
        SwingComponents.colorButton(checkFullListButton, SwingComponents.ACCENT_COLOR);
        SwingComponents.colorButton(showListButton, SwingComponents.ACTION_COLOR);
        SwingComponents.colorButton(pickButton, SwingComponents.PICK_COLOR);
        SwingComponents.colorButton(syncButton, SwingComponents.SAVED_COLOR);
        SwingComponents.colorButton(autocompleteButton, SwingComponents.AUTOCOMPLETE_COLOR);

        // 1:2 top-to-bottom glue plus a 15px strut offset the menu stack downward.
        add(Box.createVerticalGlue());
        add(Box.createVerticalStrut(15));
        add(welcomeButton);
        add(Box.createVerticalStrut(10));
        add(aboutButton);
        add(Box.createVerticalStrut(10));
        add(trackerButton);
        add(Box.createVerticalStrut(10));
        add(topSeparator);
        add(Box.createVerticalStrut(10));
        add(checkFullListButton);
        add(Box.createVerticalStrut(10));
        add(showListButton);
        add(Box.createVerticalStrut(10));
        add(pickButton);
        add(Box.createVerticalStrut(10));
        add(syncSeparator);
        add(Box.createVerticalStrut(10));
        add(autocompleteButton);
        add(Box.createVerticalStrut(10));
        add(syncButton);
        add(Box.createVerticalGlue());
        add(Box.createVerticalGlue());
    }

    public void refreshSyncButton()
    {
        syncButton.setText(syncToolBridge.isSyncToolActive() ? SYNC_LABEL : INSTALL_LABEL);
    }
}
