package com.leaguestasks.sync;

import lombok.Value;

/**
 * One collection log slot. {@code count} is 1 when obtained and 0 when
 * missing -- the in-game dump this comes from lists unique items only, so
 * duplicate quantities aren't available.
 *
 * <p>Unlike {@link SyncPayload} these are read back as well as written:
 * the last snapshot is cached in the RuneLite config so a later sync can
 * resend it without the collection log interface being open.
 */
@Value
public class CollectionLogItem
{
    int id;
    String name;
    int count;
}
