package com.leaguestasks.ui;

import com.leaguestasks.model.TaskEntry;

public interface Navigator
{
    String CARD_START = "start";
    String CARD_PICKER = "picker";
    String CARD_LIST = "list";
    String CARD_DETAIL = "detail";
    String CARD_BROWSE = "browse";
    String CARD_SYNC = "sync";
    String CARD_SYNC_SETTINGS = "syncSettings";
    String CARD_AUTOCOMPLETE = "autocomplete";
    String CARD_WELCOME = "welcome";
    String CARD_ABOUT = "about";
    String CARD_TRACKER = "tracker";
    void goToStart();

    /**
     * Opens the picker. {@code presetDifficulty} may be
     * {@link com.leaguestasks.model.TaskRepository#ANY}.
     */
    void showPicker(String presetDifficulty);

    void showList();

    void showBrowse();

    void showSync();

    void showSyncSettings();

    void showAutocomplete();

    void showDetail(TaskEntry task, String backCard);

    void showCard(String cardName);
}
