package com.uxplima.uxmessentials.persistence.notice;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;

/**
 * Factory for the kept player notices, so the bukkit-adapter wires them from the {@link Persistence} handle without
 * naming a jOOQ type.
 */
public final class PlayerNoticeStores {

    private PlayerNoticeStores() {}

    /** The lines kept for players who were not online, over the shared persistence DSL. */
    public static PendingPlayerNotices pending(Persistence persistence, Logger log) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPendingPlayerNotices(persistence.dsl(), log);
    }
}
