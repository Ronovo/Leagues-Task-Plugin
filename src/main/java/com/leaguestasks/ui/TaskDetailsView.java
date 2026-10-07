package com.leaguestasks.ui;

import com.leaguestasks.model.TaskEntry;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Shared name/meta/description/requirements block used by the picker and
 * the read-only detail card.
 */
public class TaskDetailsView extends JPanel
{
    private final JLabel nameLabel = SwingComponents.wrappingCenteredLabel();
    private final JLabel metaLabel = SwingComponents.wrappingCenteredLabel();
    private final JLabel descriptionLabel = SwingComponents.wrappingCenteredLabel();
    private final JLabel requirementsLabel = SwingComponents.wrappingCenteredLabel();
    private final JLabel confirmationLabel = SwingComponents.wrappingCenteredLabel();

    public TaskDetailsView()
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        add(nameLabel);
        add(Box.createVerticalStrut(4));
        add(metaLabel);
        add(Box.createVerticalStrut(8));
        add(descriptionLabel);
        add(Box.createVerticalStrut(4));
        add(requirementsLabel);
        add(Box.createVerticalStrut(10));
    }

    public void addAfterRequirements(java.awt.Component... components)
    {
        for (java.awt.Component c : components)
        {
            add(c);
        }
        add(Box.createVerticalStrut(4));
        add(confirmationLabel);
    }

    public void showTask(TaskEntry t)
    {
        SwingComponents.setCenteredHtml(nameLabel, "<b>" + TextUtil.escapeHtml(t.getName()) + "</b>");
        SwingComponents.setCenteredHtml(metaLabel, formatTaskMeta(t));
        SwingComponents.setCenteredHtml(descriptionLabel, TextUtil.escapeHtml(t.getDescription()));
        SwingComponents.setCenteredHtml(requirementsLabel, buildRequirementsHtml(t));
        setConfirmation("");
    }

    /**
     * Region, difficulty, and points as one centered line. Public for tests;
     * only this view renders it.
     */
    public static String formatTaskMeta(TaskEntry t)
    {
        StringBuilder meta = new StringBuilder();
        meta.append(TextUtil.escapeHtml(t.getRegion()))
            .append(" — ")
            .append(TextUtil.escapeHtml(TextUtil.capitalize(t.getDifficulty())));
        if (t.getPoints() != null)
        {
            meta.append(" (").append(t.getPoints()).append(" pts)");
        }
        return meta.toString();
    }

    public void clear()
    {
        clear("");
    }

    public void clear(String message)
    {
        SwingComponents.setCenteredHtml(nameLabel, TextUtil.escapeHtml(message));
        SwingComponents.setCenteredHtml(metaLabel, "");
        SwingComponents.setCenteredHtml(descriptionLabel, "");
        SwingComponents.setCenteredHtml(requirementsLabel, "");
        setConfirmation("");
    }

    public void setConfirmation(String text)
    {
        SwingComponents.setCenteredHtml(confirmationLabel, TextUtil.escapeHtml(text));
    }

    /**
     * Renders {@code requirementItems} as a left-aligned bulleted list
     * (the one exception to the panel's center-everything convention),
     * or falls back to a flat "Requirements: ..." line.
     */
    public static String buildRequirementsHtml(TaskEntry t)
    {
        List<TaskEntry.RequirementItem> items = t.getRequirementItems();
        if (items == null || items.isEmpty())
        {
            return t.getRequirements() != null
                ? "Requirements: " + TextUtil.escapeHtml(t.getRequirements())
                : "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style='text-align:center'><b>Requirements:</b></div>");
        html.append("<ul style='margin:4px 0 0 0; padding-left:18px; text-align:left'>");
        for (TaskEntry.RequirementItem item : items)
        {
            html.append("<li>").append(TextUtil.escapeHtml(item.getText()));

            List<String> subItems = item.getSubItems();
            if (subItems != null && !subItems.isEmpty())
            {
                html.append("<ul style='margin:2px 0 0 0; padding-left:16px'>");
                for (String subItem : subItems)
                {
                    html.append("<li>").append(TextUtil.escapeHtml(subItem)).append("</li>");
                }
                html.append("</ul>");
            }

            html.append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }
}
