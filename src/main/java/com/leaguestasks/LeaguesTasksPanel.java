package com.leaguestasks;

import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import com.leaguestasks.sync.SyncToolBridge;
import com.leaguestasks.sync.TaskAutoDetector;
import com.leaguestasks.ui.AutocompleteCard;
import com.leaguestasks.ui.BrowseCard;
import com.leaguestasks.ui.CardPanel;
import com.leaguestasks.ui.DetailCard;
import com.leaguestasks.ui.InfoCard;
import com.leaguestasks.ui.ListCard;
import com.leaguestasks.ui.Navigator;
import com.leaguestasks.ui.PickerCard;
import com.leaguestasks.ui.StartCard;
import com.leaguestasks.ui.SwingComponents;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The plugin's sidebar panel and its only {@link PluginPanel}: nine cards
 * (start, picker, list, browse, detail, autocomplete, welcome, about, tracker)
 * switched by a {@link CardPanel}. Text and controls are center-aligned, except the
 * bulleted requirements list and the left-aligned prose of the Welcome/About/Tracker cards.
 *
 * <p>The cards are built here in {@link #init()} rather than injected, because they
 * are Swing components that must be constructed on the EDT and need this panel as
 * their {@link Navigator}.
 */
@Singleton
public class LeaguesTasksPanel extends PluginPanel implements Navigator
{
    private static final int SCROLL_SPEED_MULTIPLIER = 16;

    private final TaskRepository repository;
    private final SavedTaskStore savedTaskStore;
    private final CompletedTaskStore completedTaskStore;
    private final TaskAutoDetector taskAutoDetector;
    private final SyncToolBridge syncToolBridge;

    private final CardPanel contentPanel = new CardPanel();

    private StartCard startCard;
    private PickerCard pickerCard;
    private ListCard listCard;
    private BrowseCard browseCard;
    private DetailCard detailCard;
    private AutocompleteCard autocompleteCard;
    private boolean initialized;

    @Inject
    public LeaguesTasksPanel(TaskRepository repository, SavedTaskStore savedTaskStore,
        CompletedTaskStore completedTaskStore, TaskAutoDetector taskAutoDetector,
        SyncToolBridge syncToolBridge)
    {
        this.repository = repository;
        this.savedTaskStore = savedTaskStore;
        this.completedTaskStore = completedTaskStore;
        this.taskAutoDetector = taskAutoDetector;
        this.syncToolBridge = syncToolBridge;
    }

    /** Guarded against re-entry: the plugin can be toggled off and on. */
    void init()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        startCard = new StartCard(this, syncToolBridge);
        pickerCard = new PickerCard(repository, savedTaskStore, completedTaskStore, this);
        listCard = new ListCard(repository, savedTaskStore, this);
        browseCard = new BrowseCard(repository, completedTaskStore, this);
        detailCard = new DetailCard(completedTaskStore, savedTaskStore, this);
        autocompleteCard = new AutocompleteCard(taskAutoDetector, this);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(
            SwingComponents.PANEL_BORDER_PX, SwingComponents.PANEL_BORDER_PX,
            SwingComponents.PANEL_BORDER_PX, SwingComponents.PANEL_BORDER_PX));

        contentPanel.add(startCard, Navigator.CARD_START);
        contentPanel.add(pickerCard, Navigator.CARD_PICKER);
        contentPanel.add(listCard, Navigator.CARD_LIST);
        contentPanel.add(detailCard, Navigator.CARD_DETAIL);
        contentPanel.add(browseCard, Navigator.CARD_BROWSE);
        contentPanel.add(autocompleteCard, Navigator.CARD_AUTOCOMPLETE);
        contentPanel.add(new InfoCard("/com/leaguestasks/welcome.txt", this), Navigator.CARD_WELCOME);
        contentPanel.add(new InfoCard("/com/leaguestasks/about.txt", this), Navigator.CARD_ABOUT);
        contentPanel.add(new InfoCard("/com/leaguestasks/tracker.txt", this), Navigator.CARD_TRACKER);
        add(contentPanel, BorderLayout.CENTER);

        JScrollPane scrollPane = getScrollPane();
        if (scrollPane != null)
        {
            // PluginPanel wraps us in a JScrollPane. Its L&F border/viewport
            // is a 1px light-silver line around the whole sidebar.
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
            scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
            scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            scrollPane.getViewport().setOpaque(true);
            if (scrollPane.getViewport().getView() != null)
            {
                scrollPane.getViewport().getView().setBackground(ColorScheme.DARK_GRAY_COLOR);
            }

            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setUnitIncrement(vBar.getUnitIncrement() * SCROLL_SPEED_MULTIPLIER);
        }

        JPanel wrappedPanel = getWrappedPanel();
        wrappedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        wrappedPanel.setBorder(BorderFactory.createEmptyBorder());
        wrappedPanel.setOpaque(true);
    }

    /**
     * Re-reads whatever the cards show about completion, after the store
     * changed without this card driving it -- confirming Autocomplete
     * Tasks, or switching to another account's list.
     *
     * <p>Called from the client thread, so it hops to the EDT itself
     * rather than requiring callers to coordinate threads.
     */
    public void refreshCompletionState()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (!initialized)
            {
                return;
            }
            browseCard.refreshIfChecked();
            detailCard.refreshCompletionState();
            autocompleteCard.refreshPreview();
            listCard.refresh();
            revalidate();
            repaint();
        });
    }

    /**
     * The Sync Tool is turned on and off from RuneLite's plugin list, which
     * means leaving this panel, so opening it is when the button can be stale.
     */
    @Override
    public void onActivate()
    {
        if (initialized)
        {
            startCard.refreshSyncButton();
        }
    }

    @Override
    public void showCard(String cardName)
    {
        if (Navigator.CARD_START.equals(cardName))
        {
            startCard.refreshSyncButton();
        }
        else if (Navigator.CARD_LIST.equals(cardName))
        {
            listCard.refresh();
        }
        else if (Navigator.CARD_BROWSE.equals(cardName))
        {
            browseCard.refreshIfChecked();
        }
        contentPanel.show(cardName);
        revalidate();
        repaint();
    }

    @Override
    public void goToStart()
    {
        showCard(Navigator.CARD_START);
    }

    @Override
    public void showPicker(String presetDifficulty)
    {
        pickerCard.showBlank(presetDifficulty);
        showCard(Navigator.CARD_PICKER);
    }

    @Override
    public void showList()
    {
        showCard(Navigator.CARD_LIST);
    }

    @Override
    public void showBrowse()
    {
        browseCard.onOpened();
        showCard(Navigator.CARD_BROWSE);
    }

    @Override
    public void showAutocomplete()
    {
        autocompleteCard.onOpened();
        showCard(Navigator.CARD_AUTOCOMPLETE);
    }

    @Override
    public void showDetail(TaskEntry task, String backCard)
    {
        detailCard.showTask(task, backCard);
        showCard(Navigator.CARD_DETAIL);
    }
}
