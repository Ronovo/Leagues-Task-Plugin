package com.leaguestasks.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.leaguestasks.ConfigKeys;
import com.leaguestasks.ui.TextUtil;
import java.lang.reflect.Type;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared per-account storage plumbing for the three things this plugin keeps
 * one copy of per character: completed tasks, saved tasks, and the cached
 * collection log dump. Each owner constructs one with its own key prefix.
 *
 * <p>It holds the active account, keeps {@link ConfigKeys#LAST_ACCOUNT} in
 * step (so a logged-out panel still shows the last character's data),
 * resolves the storage key, and reads/writes JSON -- logging a warning that
 * names the key (never the payload) rather than silently starting over when a
 * stored blob cannot be parsed.
 */
public final class AccountScopedConfig
{
    private static final Logger log = LoggerFactory.getLogger(AccountScopedConfig.class);

    private final ConfigManager configManager;
    private final Gson gson;
    private String account;

    public AccountScopedConfig(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    /**
     * Points storage at one account's data, writing {@link
     * ConfigKeys#LAST_ACCOUNT} whenever the account actually changes.
     *
     * @return true if the active account changed -- callers may need to drop
     * in-memory caches and repaint; false for a repeat call or an unusable
     * name.
     */
    public synchronized boolean setAccount(String displayName)
    {
        String normalized = TextUtil.normalizeAccountKey(displayName);
        if (normalized == null || normalized.equals(account))
        {
            return false;
        }

        account = normalized;
        configManager.setConfiguration(ConfigKeys.GROUP, ConfigKeys.LAST_ACCOUNT, normalized);
        return true;
    }

    public synchronized String account()
    {
        return account;
    }

    /**
     * {@code prefix + account}, or {@code prefix +} the last account seen
     * when logged out, or null when no account has ever been seen.
     */
    public synchronized String storageKey(String prefix)
    {
        if (account != null)
        {
            return prefix + account;
        }

        String last = configManager.getConfiguration(ConfigKeys.GROUP, ConfigKeys.LAST_ACCOUNT);
        return TextUtil.isBlank(last) ? null : prefix + last;
    }

    public String readRaw(String key)
    {
        return key == null ? null : configManager.getConfiguration(ConfigKeys.GROUP, key);
    }

    public void write(String key, Object value)
    {
        configManager.setConfiguration(ConfigKeys.GROUP, key, gson.toJson(value));
    }

    /**
     * Parses {@code json} as {@code type}. Blank input and a parse failure
     * both yield {@code fallback.get()}; a parse failure additionally logs a
     * warning against {@code keyForLog} (the config key, not the contents).
     */
    public <T> T parse(String keyForLog, String json, Type type, Supplier<T> fallback)
    {
        if (TextUtil.isBlank(json))
        {
            return fallback.get();
        }

        try
        {
            T parsed = gson.fromJson(json, type);
            return parsed != null ? parsed : fallback.get();
        }
        catch (JsonSyntaxException e)
        {
            log.warn("Discarding unreadable data at config key {} ({})", keyForLog, e.getMessage());
            return fallback.get();
        }
    }

    public <T> T read(String key, Type type, Supplier<T> fallback)
    {
        return parse(key, readRaw(key), type, fallback);
    }
}
