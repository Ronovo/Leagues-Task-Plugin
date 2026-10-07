package com.leaguestasks.ui;

import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Paginated browse of the full task list with completion overlay. Each row
 * requires an explicit Check before opening detail.
 */
public class BrowseCard extends JPanel
{
    private static final int PAGE_SIZE = 20;

    static final String STATUS_ANY = "Any";
    static final String STATUS_INCOMPLETE = "Incomplete";
    static final String STATUS_COMPLETED = "Completed";

    private final TaskRepository repository;
    private final CompletedTaskStore completedTaskStore;
    private final Navigator navigator;
    private final FilterGroup filters;
    private final JComboBox<String> status = SwingComponents.centeredComboBox();
    private final JLabel browseCountLabel;
    private final JPanel browseRowsPanel;
    private final JLabel browsePageLabel;
    private final JButton browsePrevButton;
    private final JButton browseNextButton;

    private int browsePage;
    private boolean browseChecked;
    private List<TaskEntry> cachedMatches = Collections.emptyList();

    public BrowseCard(TaskRepository repository, CompletedTaskStore completedTaskStore, Navigator navigator)
    {
        this.repository = repository;
        this.completedTaskStore = completedTaskStore;
        this.navigator = navigator;
        this.filters = new FilterGroup(repository, null, this::onFilterChange);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);
        add(SwingComponents.headerLabel("Task Picker"));
        add(Box.createVerticalStrut(8));
        add(filters.buildPanel());

        status.setModel(new DefaultComboBoxModel<>(new String[] {
            STATUS_ANY, STATUS_INCOMPLETE, STATUS_COMPLETED
        }));
        status.addActionListener(e -> onFilterChange());
        add(SwingComponents.centeredLabel("Status"));
        add(status);
        add(Box.createVerticalStrut(10));

        JPanel checkRow = new JPanel();
        checkRow.setLayout(new BoxLayout(checkRow, BoxLayout.X_AXIS));
        SwingComponents.stylePanel(checkRow);
        checkRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, checkRow.getPreferredSize().height));

        JButton resetButton = SwingComponents.centeredButton("Reset");
        resetButton.addActionListener(e -> resetFilters());

        JButton checkButton = SwingComponents.centeredButton("Check");
        checkButton.addActionListener(e -> {
            browseChecked = true;
            browsePage = 0;
            refreshList();
        });

        checkRow.add(resetButton);
        checkRow.add(Box.createHorizontalGlue());
        checkRow.add(checkButton);
        add(checkRow);
        add(Box.createVerticalStrut(10));

        browseCountLabel = SwingComponents.wrappingCenteredLabel();
        add(browseCountLabel);
        add(Box.createVerticalStrut(8));

        browseRowsPanel = new JPanel();
        browseRowsPanel.setLayout(new BoxLayout(browseRowsPanel, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(browseRowsPanel);
        add(browseRowsPanel);
        add(Box.createVerticalStrut(8));

        browsePageLabel = SwingComponents.wrappingCenteredLabel();
        add(browsePageLabel);
        add(Box.createVerticalStrut(6));

        JPanel pageButtons = new JPanel();
        pageButtons.setLayout(new BoxLayout(pageButtons, BoxLayout.X_AXIS));
        SwingComponents.stylePanel(pageButtons);
        pageButtons.setAlignmentX(Component.CENTER_ALIGNMENT);
        browsePrevButton = SwingComponents.centeredButton("Previous");
        browsePrevButton.addActionListener(e -> {
            browsePage--;
            renderPage();
        });
        browseNextButton = SwingComponents.centeredButton("Next");
        browseNextButton.addActionListener(e -> {
            browsePage++;
            renderPage();
        });
        pageButtons.add(browsePrevButton);
        pageButtons.add(Box.createHorizontalStrut(10));
        pageButtons.add(browseNextButton);
        add(pageButtons);
        add(Box.createVerticalStrut(10));

        JButton backButton = SwingComponents.centeredButton("Back");
        backButton.addActionListener(e -> navigator.goToStart());
        add(backButton);
        filters.refresh();
    }

    public void onOpened()
    {
        browsePage = 0;
        browseChecked = false;
        resetResults();
    }

    public void refreshIfChecked()
    {
        if (browseChecked)
        {
            refreshList();
        }
    }

    private void onFilterChange()
    {
        browsePage = 0;
        if (browseChecked)
        {
            refreshList();
        }
        else
        {
            resetResults();
        }
    }

    private void resetFilters()
    {
        browseChecked = false;
        browsePage = 0;
        filters.setFilters(TaskRepository.ANY, TaskRepository.ANY, TaskRepository.ANY);
        status.setSelectedItem(STATUS_ANY);
        filters.refresh();
        resetResults();
    }

    private void resetResults()
    {
        cachedMatches = Collections.emptyList();
        browseRowsPanel.removeAll();
        browseRowsPanel.revalidate();
        browseRowsPanel.repaint();
        SwingComponents.setCenteredHtml(browseCountLabel, "Press Check to see matching tasks");
        SwingComponents.setCenteredHtml(browsePageLabel, "");
        browsePrevButton.setEnabled(false);
        browseNextButton.setEnabled(false);
    }

    private void refreshList()
    {
        List<TaskEntry> matches = repository.allMatching(
            filters.region(), filters.difficulty(), filters.type());
        cachedMatches = filterByStatus(matches, selectedStatus(), completedTaskStore.completedTaskIds());
        renderPage();
    }

    static List<TaskEntry> filterByStatus(List<TaskEntry> tasks, String statusValue, Set<String> completedIds)
    {
        if (STATUS_INCOMPLETE.equals(statusValue))
        {
            return tasks.stream()
                .filter(t -> !completedIds.contains(t.getId()))
                .collect(Collectors.toList());
        }
        if (STATUS_COMPLETED.equals(statusValue))
        {
            return tasks.stream()
                .filter(t -> completedIds.contains(t.getId()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>(tasks);
    }

    static String formatCountLabel(String statusValue, List<TaskEntry> matches, Set<String> completedIds)
    {
        if (STATUS_INCOMPLETE.equals(statusValue))
        {
            return "Incomplete: " + matches.size();
        }
        if (STATUS_COMPLETED.equals(statusValue))
        {
            return "Completed: " + matches.size();
        }

        int completedCount = 0;
        for (TaskEntry t : matches)
        {
            if (completedIds.contains(t.getId()))
            {
                completedCount++;
            }
        }
        return "Completed: " + completedCount + " / " + matches.size();
    }

    private String selectedStatus()
    {
        String item = (String) status.getSelectedItem();
        return item != null ? item : STATUS_ANY;
    }

    private void renderPage()
    {
        List<TaskEntry> matches = cachedMatches;
        Set<String> completedIds = completedTaskStore.completedTaskIds();

        int totalPages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (browsePage < 0)
        {
            browsePage = 0;
        }
        else if (browsePage >= totalPages)
        {
            browsePage = totalPages - 1;
        }

        SwingComponents.setCenteredHtml(browseCountLabel,
            formatCountLabel(selectedStatus(), matches, completedIds));

        int fromIndex = Math.min(browsePage * PAGE_SIZE, matches.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, matches.size());

        browseRowsPanel.removeAll();
        for (TaskEntry t : matches.subList(fromIndex, toIndex))
        {
            JButton row = SwingComponents.fullWidthRowButton(SwingComponents.centeredHtml(TextUtil.escapeHtml(t.getName())));

            if (completedIds.contains(t.getId()))
            {
                row.setOpaque(true);
                row.setBorderPainted(false);
                row.setForeground(Color.WHITE);
                row.setBackground(SwingComponents.SAVED_COLOR);
            }

            row.addActionListener(e -> navigator.showDetail(t, Navigator.CARD_BROWSE));
            browseRowsPanel.add(row);
            browseRowsPanel.add(Box.createVerticalStrut(4));
        }
        browseRowsPanel.revalidate();
        browseRowsPanel.repaint();

        SwingComponents.setCenteredHtml(browsePageLabel, "Page " + (browsePage + 1) + " of " + totalPages);
        browsePrevButton.setEnabled(browsePage > 0);
        browseNextButton.setEnabled(browsePage < totalPages - 1);
    }
}
