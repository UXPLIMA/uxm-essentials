package com.uxplima.uxmessentials.shared.application.settings;

import java.util.List;
import java.util.UUID;

/**
 * Per-player settings a backend holds in memory for the players online on it, while the shared database keeps them
 * between backends. The platform tells it when a player arrives, leaves, or had their settings changed elsewhere.
 */
public interface HeldSettings {

    /** Read the stored settings of {@code player}, who is logging in. Does I/O: call it off the tick. */
    void load(UUID player);

    /** Let go of {@code player}, who left or whose login did not go through. */
    void forget(UUID player);

    /** Read the settings of {@code player} again on the async lane, if they are held here. */
    void refresh(UUID player);

    /** Read the settings of every player held here again. Does I/O: call it off the tick. */
    void refreshAll();

    /** Every one of {@code parts}, told the same thing in the order given. */
    static HeldSettings of(HeldSettings... parts) {
        List<HeldSettings> all = List.of(parts);
        return new HeldSettings() {
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

            @Override
            public void refreshAll() {
                all.forEach(HeldSettings::refreshAll);
            }
        };
    }
}
