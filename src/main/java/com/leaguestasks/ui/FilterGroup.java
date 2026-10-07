package com.leaguestasks.ui;

import com.leaguestasks.model.TaskRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * Three mutually-cascading filter dropdowns (Region/Difficulty/Activity
 * type): whichever combination is selected, each dropdown only offers
 * options that still have at least one matching task given the other two.
 */
class FilterGroup
{
    final JComboBox<String> region = SwingComponents.centeredComboBox();
    final JComboBox<String> difficulty = SwingComponents.centeredComboBox();
    final JComboBox<String> type = SwingComponents.centeredComboBox();
    private final TaskRepository repository;
    private final Supplier<Set<String>> excludeIds;
    private final Runnable onChange;
    /** Suppresses recursive refresh while programmatically updating combo models. */
    private boolean refreshing;

    FilterGroup(TaskRepository repository, Supplier<Set<String>> excludeIds, Runnable onChange)
    {
        this.repository = repository;
        this.excludeIds = excludeIds != null ? excludeIds : Collections::emptySet;
        this.onChange = onChange;
        region.addActionListener(e -> refresh());
        difficulty.addActionListener(e -> refresh());
        type.addActionListener(e -> refresh());
    }

    /**
     * Recomputes each dropdown's option list from the other two's
     * current selections, dropping any selection that's no longer
     * eligible (falling back to "Any").
     */
    void refresh()
    {
        if (refreshing)
        {
            return;
        }

        refreshing = true;
        try
        {
            refreshModels();
        }
        finally
        {
            refreshing = false;
        }

        if (onChange != null)
        {
            onChange.run();
        }
    }

    /**
     * Sets all three filters without firing {@code onChange} three times.
     */
    void setFilters(String regionValue, String difficultyValue, String typeValue)
    {
        refreshing = true;
        try
        {
            region.setSelectedItem(regionValue);
            difficulty.setSelectedItem(difficultyValue);
            type.setSelectedItem(typeValue);
            refreshModels();
        }
        finally
        {
            refreshing = false;
        }
    }

    private void refreshModels()
    {
        Set<String> excluded = excludeIds.get();
        String r = selected(region);
        String d = selected(difficulty);
        String t = selected(type);

        if (!repository.eligibleRegions(d, t, excluded).contains(r))
        {
            r = TaskRepository.ANY;
        }
        if (!repository.eligibleDifficulties(r, t, excluded).contains(d))
        {
            d = TaskRepository.ANY;
        }
        if (!repository.eligibleActivityTypes(r, d, excluded).contains(t))
        {
            t = TaskRepository.ANY;
        }

        applyModel(region, repository.eligibleRegions(d, t, excluded), r);
        applyModel(difficulty, repository.eligibleDifficulties(r, t, excluded), d);
        applyModel(type, repository.eligibleActivityTypes(r, d, excluded), t);
    }

    String region()
    {
        return selected(region);
    }

    String difficulty()
    {
        return selected(difficulty);
    }

    String type()
    {
        return selected(type);
    }

    JPanel buildPanel()
    {
        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(filters);

        filters.add(SwingComponents.centeredLabel("Region"));
        filters.add(region);
        filters.add(Box.createVerticalStrut(6));
        filters.add(SwingComponents.centeredLabel("Difficulty"));
        filters.add(difficulty);
        filters.add(Box.createVerticalStrut(6));
        filters.add(SwingComponents.centeredLabel("Activity type"));
        filters.add(type);
        filters.add(Box.createVerticalStrut(10));

        return filters;
    }

    private static void applyModel(JComboBox<String> combo, List<String> items, String selection)
    {
        combo.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
        combo.setSelectedItem(selection);
    }

    private static String selected(JComboBox<String> combo)
    {
        String item = (String) combo.getSelectedItem();
        return item != null ? item : TaskRepository.ANY;
    }
}
