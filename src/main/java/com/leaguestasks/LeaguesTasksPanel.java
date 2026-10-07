package com.leaguestasks;

import com.leaguestasks.model.CompletedTaskStore;
import com.leaguestasks.model.SavedTaskStore;
import com.leaguestasks.model.TaskEntry;
import com.leaguestasks.model.TaskRepository;
import com.leaguestasks.sync.ProgressSyncClient;
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
import com.leaguestasks.ui.SyncCard;
import com.leaguestasks.ui.SyncSettingsCard;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The plugin's sidebar panel and its only {@link PluginPanel}: eleven cards
 * (start, picker, list, browse, detail, sync, sync settings, autocomplete, welcome, about, tracker)
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
    private final ProgressSyncClient progressSyncClient;
    private final TaskAutoDetector taskAutoDetector;
    private final ConfigManager configManager;
    private final LeaguesTasksConfig config;

    private final CardPanel contentPanel = new CardPanel();

    private PickerCard pickerCard;
    private ListCard listCard;
    private BrowseCard browseCard;
    private DetailCard detailCard;
    private SyncCard syncCard;
    private AutocompleteCard autocompleteCard;
    private SyncSettingsCard syncSettingsCard;
    private boolean initialized;

    @Inject
    public LeaguesTasksPanel(TaskRepository repository, SavedTaskStore savedTaskStore,
        CompletedTaskStore completedTaskStore, ProgressSyncClient progressSyncClient,
        TaskAutoDetector taskAutoDetector, ConfigManager configManager, LeaguesTasksConfig config)
    {
        this.repository = repository;
        this.savedTaskStore = savedTaskStore;
        this.completedTaskStore = completedTaskStore;
        this.progressSyncClient = progressSyncClient;
        this.taskAutoDetector = taskAutoDetector;
        this.configManager = configManager;
        this.config = config;
    }

    /** Guarded against re-entry: the plugin can be toggled off and on. */
    void init()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        pickerCard = new PickerCard(repository, savedTaskStore, completedTaskStore, this);
        listCard = new ListCard(repository, savedTaskStore, this);
        browseCard = new BrowseCard(repository, completedTaskStore, this);
        detailCard = new DetailCard(completedTaskStore, savedTaskStore, this);
        syncCard = new SyncCard(progressSyncClient, this);
        autocompleteCard = new AutocompleteCard(taskAutoDetector, this);
        syncSettingsCard = new SyncSettingsCard(configManager, config, this);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(
            SwingComponents.PANEL_BORDER_PX, SwingComponents.PANEL_BORDER_PX,
            SwingComponents.PANEL_BORDER_PX, SwingComponents.PANEL_BORDER_PX));

        contentPanel.add(new StartCard(this), Navigator.CARD_START);
        contentPanel.add(pickerCard, Navigator.CARD_PICKER);
        contentPanel.add(listCard, Navigator.CARD_LIST);
        contentPanel.add(detailCard, Navigator.CARD_DETAIL);
        contentPanel.add(browseCard, Navigator.CARD_BROWSE);
        contentPanel.add(syncCard, Navigator.CARD_SYNC);
        contentPanel.add(autocompleteCard, Navigator.CARD_AUTOCOMPLETE);
        contentPanel.add(syncSettingsCard, Navigator.CARD_SYNC_SETTINGS);
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

    @Override
    public void showCard(String cardName)
    {
        if (Navigator.CARD_LIST.equals(cardName))
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
    public void showSync()
    {
        syncCard.onOpened();
        showCard(Navigator.CARD_SYNC);
    }

    @Override
    public void showAutocomplete()
    {
        autocompleteCard.onOpened();
        showCard(Navigator.CARD_AUTOCOMPLETE);
    }

    @Override
    public void showSyncSettings()
    {
        syncSettingsCard.onOpened();
        showCard(Navigator.CARD_SYNC_SETTINGS);
    }

    @Override
    public void showDetail(TaskEntry task, String backCard)
    {
        detailCard.showTask(task, backCard);
        showCard(Navigator.CARD_DETAIL);
    }
}
