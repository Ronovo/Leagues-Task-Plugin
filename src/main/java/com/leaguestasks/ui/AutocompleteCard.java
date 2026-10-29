package com.leaguestasks.ui;

import com.leaguestasks.sync.AutocompletePreview;
import com.leaguestasks.sync.TaskAutoDetector;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Preview and confirm flow for marking tasks complete from stats the client
 * can read (quests, skill levels, boss kills, etc.).
 */
public class AutocompleteCard extends JPanel
{
    private final TaskAutoDetector taskAutoDetector;

    private final JLabel summaryLabel;
    private final JButton autocompleteButton;
    private final JButton cancelButton;
    private final JLabel statusLabel;

    /** How many the last preview found; also what the confirm click promises. */
    private int pendingCount;
    /** Breakdown rows from the last preview; empty when none are pending. */
    private List<AutocompletePreview.CategoryCount> pendingCategories = Collections.emptyList();
    /** Whether the category breakdown is shown instead of the click prompt. */
    private boolean showingTotals;
    /** True once the button has been armed and is showing the confirm wording. */
    private boolean awaitingConfirm;

    public AutocompleteCard(TaskAutoDetector taskAutoDetector, Navigator navigator)
    {
        this.taskAutoDetector = taskAutoDetector;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        SwingComponents.stylePanel(this);

        add(Box.createVerticalGlue());

        add(SwingComponents.headerLabel("Autocomplete Tasks"));
        add(Box.createVerticalStrut(6));

        summaryLabel = SwingComponents.wrappingCenteredLabel();
        summaryLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (pendingCategories.isEmpty())
                {
                    return;
                }
                showingTotals = !showingTotals;
                renderSummary();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (!pendingCategories.isEmpty())
                {
                    summaryLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                summaryLabel.setCursor(Cursor.getDefaultCursor());
            }
        });
        add(summaryLabel);
        add(Box.createVerticalStrut(10));

        autocompleteButton = SwingComponents.centeredButton("Autocomplete Tasks");
        autocompleteButton.addActionListener(e -> onAutocompleteClicked());
        SwingComponents.colorButton(autocompleteButton, SwingComponents.AUTOCOMPLETE_COLOR);
        add(autocompleteButton);

        add(Box.createVerticalStrut(4));
        cancelButton = SwingComponents.centeredButton("Cancel");
        cancelButton.addActionListener(e -> disarmConfirm());
        cancelButton.setVisible(false);
        add(cancelButton);

        add(Box.createVerticalStrut(6));
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
        disarmConfirm();
        refreshPreview();
    }

    /**
     * Re-counts what could be autocompleted. Also called after the panel
     * learns the account changed, so the numbers belong to whoever is
     * actually logged in.
     */
    public void refreshPreview()
    {
        pendingCount = 0;
        pendingCategories = Collections.emptyList();
        showingTotals = false;
        autocompleteButton.setEnabled(false);
        summaryLabel.setCursor(Cursor.getDefaultCursor());
        SwingComponents.setCenteredHtml(summaryLabel, "Checking your stats and quest log...");

        taskAutoDetector.preview(this::showPreview);
    }

    private void showPreview(AutocompletePreview preview)
    {
        // A preview can land after the player has already armed the confirm
        // (they levelled up mid-decision); re-arming from scratch keeps the
        // number on the button honest.
        disarmConfirm();

        if (preview.getUnavailableReason() != null)
        {
            pendingCount = 0;
            pendingCategories = Collections.emptyList();
            showingTotals = false;
            autocompleteButton.setEnabled(false);
            summaryLabel.setCursor(Cursor.getDefaultCursor());
            SwingComponents.setCenteredHtml(summaryLabel,
                TextUtil.escapeHtml(preview.getUnavailableReason()));
            return;
        }

        pendingCount = preview.getTotal();
        autocompleteButton.setEnabled(pendingCount > 0);

        if (pendingCount == 0)
        {
            pendingCategories = Collections.emptyList();
            showingTotals = false;
            summaryLabel.setCursor(Cursor.getDefaultCursor());
            SwingComponents.setCenteredHtml(summaryLabel,
                "Nothing left to autocomplete -- every task your stats and "
                    + "quest log can prove is already marked complete.");
            return;
        }

        pendingCategories = preview.getCategories();
        showingTotals = false;
        renderSummary();
    }

    private void renderSummary()
    {
        StringBuilder html = new StringBuilder();
        html.append("<b>").append(pendingCount)
            .append(pendingCount == 1 ? "</b> task can" : "</b> tasks can")
            .append(" be marked complete from your stats and quest log");

        if (showingTotals)
        {
            html.append(":<br><br>");
            for (AutocompletePreview.CategoryCount category : pendingCategories)
            {
                html.append(category.getCount()).append("&nbsp;&nbsp;")
                    .append(TextUtil.escapeHtml(category.getLabel())).append("<br>");
            }
        }
        else
        {
            html.append(".<br><br><span style='color: ")
                .append(SwingComponents.hex(SwingComponents.ACCENT_COLOR))
                .append("; text-decoration: underline;'>Click here for list of totals</span>");
        }

        SwingComponents.setCenteredHtml(summaryLabel, html.toString());
    }

    /**
     * First click arms, second commits. There is no un-mark, so the count
     * gets stated one more time on the button itself before anything is
     * written.
     */
    private void onAutocompleteClicked()
    {
        if (!awaitingConfirm)
        {
            awaitingConfirm = true;
            autocompleteButton.setText("Confirm: mark " + pendingCount + " complete");
            SwingComponents.colorButton(autocompleteButton, SwingComponents.ACCENT_COLOR);
            cancelButton.setVisible(true);
            SwingComponents.setCenteredHtml(statusLabel,
                "This cannot be undone from the panel.");
            revalidate();
            repaint();
            return;
        }

        disarmConfirm();
        autocompleteButton.setEnabled(false);
        SwingComponents.setCenteredHtml(statusLabel, "Marking...");

        taskAutoDetector.apply(marked ->
        {
            statusLabel.setForeground(SwingComponents.SAVED_COLOR);
            SwingComponents.setCenteredHtml(statusLabel,
                marked == 1 ? "1 task marked complete." : marked + " tasks marked complete.");
            // Recount so the summary drops to zero (or picks up anything
            // that became true while the write was in flight).
            refreshPreview();
        });
    }

    private void disarmConfirm()
    {
        awaitingConfirm = false;
        autocompleteButton.setText("Autocomplete Tasks");
        SwingComponents.colorButton(autocompleteButton, SwingComponents.AUTOCOMPLETE_COLOR);
        cancelButton.setVisible(false);
        statusLabel.setForeground(SwingComponents.ERROR_COLOR);
        revalidate();
        repaint();
    }
}
