package com.leaguestasks.sync;

/**
 * Result of any {@link ProgressSyncClient} sync call. Always invoked exactly
 * once, and always on the Swing EDT, so callers (Swing views) need no thread
 * coordination.
 */
public interface SyncCallback
{
    void onResult(SyncOutcome outcome, String message);
}
