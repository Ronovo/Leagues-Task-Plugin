package com.leaguestasks.ui;

import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Random task picker: filter by region/difficulty/activity type, roll a task,
 * then save or replace a slot on the saved list.
 */
public class PickerCard extends JPanel
{
    private final TaskRepository repository;
    private final SavedTaskStore savedTaskStore;
    private final CompletedTaskStore completedTaskStore;
    private final FilterGroup filters;
    private final TaskDetailsView details = new TaskDetailsView();
    private final JButton saveButton;
    private TaskEntry currentTask;

    public PickerCard(TaskRepository repository, SavedTaskStore savedTaskStore,
        CompletedTaskStore completedTaskStore, Navigator navigator)
    {
        this.repository = repository;
        this.savedTaskStore = savedTaskStore;
        this.completedTaskStore = completedTaskStore;
        this.filters = new FilterGroup(repository, completedTaskStore::completedTaskIds, null);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);
        add(SwingComponents.headerLabel("Random Task Picker"));
        add(Box.createVerticalStrut(8));
        add(filters.buildPanel());

        saveButton = SwingComponents.centeredButton("Save Task");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveCurrentTask());

        JButton pickNewButton = SwingComponents.centeredButton("Pick New Task");
        pickNewButton.addActionListener(e -> pickRandomTask());

        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.goToStart());

        details.addAfterRequirements(
            saveButton,
            Box.createVerticalStrut(6),
            pickNewButton,
            Box.createVerticalStrut(6),
            backButton
        );
        add(details);
        filters.refresh();
    }

    public void showBlank(String presetDifficulty)
    {
        currentTask = null;
        saveButton.setEnabled(false);
        details.clear();
        if (!TaskRepository.ANY.equals(presetDifficulty))
        {
            filters.setFilters(TaskRepository.ANY, presetDifficulty, TaskRepository.ANY);
        }
        else
        {
            filters.difficulty.setSelectedItem(presetDifficulty);
        }
        filters.refresh();
    }

    private void pickRandomTask()
    {
        Optional<TaskEntry> task = repository.randomTask(
            filters.region(), filters.difficulty(), filters.type(),
            completedTaskStore.completedTaskIds());

        if (!task.isPresent())
        {
            currentTask = null;
            saveButton.setEnabled(false);
            details.clear("No tasks match those filters");
            return;
        }

        currentTask = task.get();
        saveButton.setEnabled(true);
        details.showTask(currentTask);
    }

    private void saveCurrentTask()
    {
        if (currentTask == null)
        {
            return;
        }

        if (SavedTaskPrompt.saveConfirmingReplace(this, savedTaskStore, currentTask))
        {
            details.setConfirmation("Task Saved to List");
        }
    }
}
