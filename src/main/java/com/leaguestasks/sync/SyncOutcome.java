package com.leaguestasks.sync;

/**
 * How a {@link ProgressSyncClient} sync call ended, for the UI to decide
 * how to color/word its status message.
 */
public enum SyncOutcome
{
    SUCCESS,
    MISSING_CONFIG,
    NOT_LOGGED_IN,
    UNAUTHORIZED,
    HTTP_ERROR,
    NETWORK_ERROR,
    /** {@link ProgressSyncClient#syncCollectionLog} only: nothing captured yet, and the interface isn't open right now either. */
    NO_COLLECTION_LOG
}
