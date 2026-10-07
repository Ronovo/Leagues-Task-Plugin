package com.leaguestasks.ui;

import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.awt.Color;
import java.util.Optional;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Saved-task list view: one row per difficulty tier the player has starred.
 */
public class ListCard extends JPanel
{
    private final TaskRepository repository;
    private final SavedTaskStore savedTaskStore;
    private final Navigator navigator;
    private final JPanel listRowsPanel;

    public ListCard(TaskRepository repository, SavedTaskStore savedTaskStore, Navigator navigator)
    {
        this.repository = repository;
        this.savedTaskStore = savedTaskStore;
        this.navigator = navigator;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        add(SwingComponents.headerLabel("My List"));
        add(Box.createVerticalStrut(8));

        listRowsPanel = new JPanel();
        listRowsPanel.setLayout(new BoxLayout(listRowsPanel, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(listRowsPanel);
        add(listRowsPanel);

        add(Box.createVerticalStrut(10));
        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.goToStart());
        add(backButton);
    }

    public void refresh()
    {
        listRowsPanel.removeAll();
        savedTaskStore.pruneCompleted();

        for (String difficulty : repository.difficultyTiersInOrder())
        {
            Optional<TaskEntry> saved = savedTaskStore.getSaved(difficulty);

            JButton row = SwingComponents.fullWidthRowButton(difficulty);
            row.setOpaque(true);
            row.setBorderPainted(false);
            row.setFocusPainted(false);
            row.setForeground(Color.WHITE);

            JLabel subLabel = SwingComponents.wrappingCenteredLabel();
            SwingComponents.setCenteredHtml(subLabel,
                saved.isPresent() ? TextUtil.escapeHtml(saved.get().getName()) : "(empty)");

            if (saved.isPresent())
            {
                row.setBackground(SwingComponents.SAVED_COLOR);
                TaskEntry task = saved.get();
                row.addActionListener(e -> navigator.showDetail(task, Navigator.CARD_LIST));
            }
            else
            {
                row.setBackground(SwingComponents.EMPTY_COLOR);
                row.addActionListener(e -> navigator.showPicker(difficulty));
            }

            listRowsPanel.add(row);
            listRowsPanel.add(Box.createVerticalStrut(4));
            listRowsPanel.add(subLabel);
            listRowsPanel.add(Box.createVerticalStrut(12));
        }

        listRowsPanel.revalidate();
        listRowsPanel.repaint();
    }
}
