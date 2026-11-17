package com.uxplima.uxmessentials.shared.application.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where the on/off switches a player sets for themselves ({@code /msgtoggle}, {@code /socialspy}) are kept between
 * backends: the shared database. A port because it is a storage boundary. Both calls do I/O and run off the tick.
 */
public interface PlayerSwitchStore {

    /** The switches among {@code names} that {@code player} has set; a switch they never set is absent. */
    Map<String, Boolean> load(UUID player, Set<String> names);

    /** Store each of {@code switches} for {@code player}; a switch not named keeps its row. */
    void save(UUID player, Map<String, Boolean> switches);
}
