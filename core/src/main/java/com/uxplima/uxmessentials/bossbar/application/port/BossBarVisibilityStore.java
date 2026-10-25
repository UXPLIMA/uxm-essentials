package com.uxplima.uxmessentials.bossbar.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Whether a player has hidden their boss bars. The choice is kept with the player and survives a relog. */
public interface BossBarVisibilityStore {

    /** Whether {@code who} sees no bars; a player who never chose reads the server's default. */
    boolean hidden(PlayerRef who);

    /** Keep {@code who}'s choice. */
    void setHidden(PlayerRef who, boolean hidden);
}
