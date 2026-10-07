package com.leaguestasks.ui;

import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

public class DetailCard extends JPanel
{
    private final CompletedTaskStore completedTaskStore;
    private final SavedTaskStore savedTaskStore;
    private final TaskDetailsView details = new TaskDetailsView();
    private final JButton completeButton;
    private TaskEntry detailTask;
    private String backTarget;

    public DetailCard(CompletedTaskStore completedTaskStore, SavedTaskStore savedTaskStore,
        Navigator navigator)
    {
        this.completedTaskStore = completedTaskStore;
        this.savedTaskStore = savedTaskStore;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        completeButton = SwingComponents.centeredButton("Complete Task");
        completeButton.addActionListener(e -> completeDetailTask());

        JButton assignButton = SwingComponents.centeredButton("Assign Task");
        assignButton.addActionListener(e -> assignDetailTask());

        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.showCard(backTarget));

        details.addAfterRequirements(
            completeButton,
            Box.createVerticalStrut(6),
            assignButton,
            Box.createVerticalStrut(6),
            backButton
        );
        add(details);
    }

    public void showTask(TaskEntry t, String backCard)
    {
        detailTask = t;
        backTarget = backCard;
        details.showTask(t);
        updateCompleteButton();
    }

    /**
     * Re-reads completion state for the task on screen -- the store can
     * change underneath this card when the player confirms Autocomplete
     * Tasks.
     */
    public void refreshCompletionState()
    {
        updateCompleteButton();
    }

    private void updateCompleteButton()
    {
        boolean alreadyDone = detailTask != null
            && completedTaskStore.completedTaskIds().contains(detailTask.getId());
        completeButton.setEnabled(!alreadyDone);
    }

    private void completeDetailTask()
    {
        if (detailTask == null || !completedTaskStore.markCompleted(detailTask))
        {
            return;
        }

        completeButton.setEnabled(false);
        if (Navigator.CARD_LIST.equals(backTarget))
        {
            savedTaskStore.clear(detailTask.getDifficulty());
            details.setConfirmation("Task marked complete and removed from list");
        }
        else
        {
            details.setConfirmation("Task marked complete");
        }
    }

    private void assignDetailTask()
    {
        if (detailTask == null)
        {
            return;
        }

        if (SavedTaskPrompt.saveConfirmingReplace(this, savedTaskStore, detailTask))
        {
            details.setConfirmation("Task Saved to List");
        }
    }
}
