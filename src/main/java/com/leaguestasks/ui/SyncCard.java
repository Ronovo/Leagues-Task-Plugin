package com.leaguestasks.ui;

import com.leaguestasks.sync.ProgressSyncClient;
import com.leaguestasks.sync.SyncCallback;
import com.leaguestasks.sync.SyncOutcome;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Sync actions for sending progress to a self-hosted tracker, laid out as a
 * four-step flow: configure the server, sync the collection log (done from
 * the in-game interface, so Step 2 is just instructions), do a first full
 * sync, then keep individual sections up to date. Each section-specific sync
 * button sends only that section, leaving whatever the tracker already has
 * for the others untouched. Sync Everything sends every section in one
 * request. Server URL/token live in {@link SyncSettingsCard}, reached from
 * Step 1.
 */
public class SyncCard extends JPanel
{
    private final ProgressSyncClient progressSyncClient;

    private final JButton syncTasksButton;
    private final JButton syncQuestsButton;
    private final JButton syncAchievementDiaryButton;
    private final JButton syncCombatAchievementsButton;
    private final JButton syncEverythingButton;
    private final JLabel statusLabel;

    public SyncCard(ProgressSyncClient progressSyncClient, Navigator navigator)
    {
        this.progressSyncClient = progressSyncClient;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        add(Box.createVerticalGlue());

        add(SwingComponents.headerLabel("Sync to Website"));
        add(Box.createVerticalStrut(6));

        JLabel syncSubheading = SwingComponents.wrappingCenteredLabel();
        SwingComponents.setCenteredHtml(syncSubheading,
            "Follow the steps in order for your first sync.");
        add(syncSubheading);

        addStepHeader("Step 1: Configure Server");
        JButton syncSettingsButton = SwingComponents.centeredButton("Sync Settings");
        syncSettingsButton.addActionListener(e -> navigator.showSyncSettings());
        SwingComponents.colorButton(syncSettingsButton, SwingComponents.EMPTY_COLOR);
        add(syncSettingsButton);

        addStepHeader("Step 2: Collection Log Sync");
        JLabel collectionLogHint = SwingComponents.wrappingCenteredLabel();
        SwingComponents.setCenteredHtml(collectionLogHint,
            "Open your Collection Log in RuneLite, then click <b>Sync</b> in its top-right corner.");
        add(collectionLogHint);

        addStepHeader("Step 3: Sync Everything (First Time Sync)");
        syncEverythingButton = SwingComponents.centeredButton("Sync Everything");
        syncEverythingButton.addActionListener(e -> onSyncClicked(progressSyncClient::sync));
        SwingComponents.colorButton(syncEverythingButton, SwingComponents.SAVED_COLOR);
        add(syncEverythingButton);

        addStepHeader("Step 4: Sync Manually");

        syncTasksButton = SwingComponents.centeredButton("Sync Tasks");
        syncTasksButton.addActionListener(e -> onSyncClicked(progressSyncClient::syncTasks));
        SwingComponents.colorButton(syncTasksButton, SwingComponents.SYNC_COLOR);
        add(syncTasksButton);
        add(Box.createVerticalStrut(10));

        syncQuestsButton = SwingComponents.centeredButton("Sync Quests");
        syncQuestsButton.addActionListener(e -> onSyncClicked(progressSyncClient::syncQuests));
        SwingComponents.colorButton(syncQuestsButton, SwingComponents.SYNC_COLOR);
        add(syncQuestsButton);
        add(Box.createVerticalStrut(10));

        syncAchievementDiaryButton = SwingComponents.centeredButton("Sync Achievement Diary");
        syncAchievementDiaryButton.addActionListener(e -> onSyncClicked(progressSyncClient::syncAchievementDiary));
        SwingComponents.colorButton(syncAchievementDiaryButton, SwingComponents.SYNC_COLOR);
        add(syncAchievementDiaryButton);
        add(Box.createVerticalStrut(10));

        syncCombatAchievementsButton = SwingComponents.centeredButton("Sync Combat Achievements");
        syncCombatAchievementsButton.addActionListener(e -> onSyncClicked(progressSyncClient::syncCombatAchievements));
        SwingComponents.colorButton(syncCombatAchievementsButton, SwingComponents.SYNC_COLOR);
        add(syncCombatAchievementsButton);

        add(Box.createVerticalStrut(14));
        statusLabel = SwingComponents.wrappingCenteredLabel();
        add(statusLabel);

        add(Box.createVerticalStrut(14));
        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.goToStart());
        add(backButton);
        add(Box.createVerticalGlue());
    }

    public void onOpened()
    {
        SwingComponents.setCenteredHtml(statusLabel, "");
    }

    /** A step heading (see {@link SwingComponents#headerLabel}) with spacing above and below. */
    private void addStepHeader(String text)
    {
        add(Box.createVerticalStrut(16));
        add(SwingComponents.headerLabel(text));
        add(Box.createVerticalStrut(6));
    }

    /** One of the six sync entry points on {@link ProgressSyncClient}. */
    private interface SyncAction
    {
        void run(SyncCallback callback);
    }

    private void onSyncClicked(SyncAction action)
    {
        setSyncButtonsEnabled(false);
        SwingComponents.setCenteredHtml(statusLabel, "Syncing...");

        action.run((outcome, message) ->
        {
            setSyncButtonsEnabled(true);
            statusLabel.setForeground(outcome == SyncOutcome.SUCCESS
                ? SwingComponents.SAVED_COLOR
                : SwingComponents.ERROR_COLOR);
            SwingComponents.setCenteredHtml(statusLabel, TextUtil.escapeHtml(message));
        });
    }

    private void setSyncButtonsEnabled(boolean enabled)
    {
        syncTasksButton.setEnabled(enabled);
        syncQuestsButton.setEnabled(enabled);
        syncAchievementDiaryButton.setEnabled(enabled);
        syncCombatAchievementsButton.setEnabled(enabled);
        syncEverythingButton.setEnabled(enabled);
    }
}
