package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@code /broadcasttoggle} switch over the switches every backend shares, so a player who turned the rotating
 * announcements off keeps them off on every backend of the network. A player who never ran the command receives them.
 */
public final class SharedBroadcastOptOut implements BroadcastOptOutStore {

    /** Whether the player receives the rotating announcements. */
    public static final String BROADCASTS = "broadcasts";

    /** Every switch the communication module keeps. */
    public static final Set<String> NAMES = Set.of(BROADCASTS);

    private final PlayerSwitches switches;

    public SharedBroadcastOptOut(PlayerSwitches switches) {
        this.switches = Objects.requireNonNull(switches, "switches");
    }

    @Override
    public boolean receivesBroadcasts(PlayerRef who) {
        return switches.isOn(who.uuid(), BROADCASTS, true);
    }

    @Override
    public boolean toggle(PlayerRef who) {
        return !switches.flip(who.uuid(), BROADCASTS, true);
    }

    @Override
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        // The choice is kept for the player's next visit; the shared switches let go of them when they leave.
    }
}
