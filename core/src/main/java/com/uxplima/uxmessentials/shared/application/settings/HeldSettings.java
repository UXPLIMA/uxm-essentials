package com.uxplima.uxmessentials.shared.application.settings;

import java.util.List;
import java.util.UUID;

/**
 * Per-player settings a backend holds in memory for the players online on it, while the shared database keeps them
 * between backends. The platform tells it when a player arrives, leaves, or had their settings changed elsewhere.
 */
public interface HeldSettings {

    /** Whether the settings of {@code player} were read and are held here. */
    boolean holds(UUID player);

    /** Read the stored settings of {@code player}, who is logging in. Does I/O: call it off the tick. */
    void load(UUID player);

    /** Let go of {@code player}, who left or whose login did not go through. */
    void forget(UUID player);

    /**
     * The settings of {@code player} changed elsewhere: read them again on the async lane if they are held here, or
     * once more in the login read that is running for them.
     */
    void refresh(UUID player);

    /** Every one of {@code parts}, told the same thing in the order given. */
    static HeldSettings of(HeldSettings... parts) {
        List<HeldSettings> all = List.of(parts);
        return new HeldSettings() {
            @Override
            public boolean holds(UUID player) {
                return all.stream().allMatch(part -> part.holds(player));
            }

            @Override
            public void load(UUID player) {
                all.forEach(part -> part.load(player));
            }

            @Override
            public void forget(UUID player) {
                all.forEach(part -> part.forget(player));
            }

            @Override
            public void refresh(UUID player) {
                all.forEach(part -> part.refresh(player));
            }
        };
    }
}
