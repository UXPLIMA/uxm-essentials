package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.network.PlayerSettingsChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the per-player settings every backend shares current across the network. A change written here is announced
 * in a {@link PlayerSettingsChanged}; a backend holding that player, or reading them for a login, reads their settings
 * again. That covers a player who reached the next backend before the change was written. The settings are held only
 * for the players online on a backend and read again at every login, so they stay out of the periodic resync: a lost
 * frame lasts until the player's next login at most.
 */
@NullMarked
public final class PlayerSettingsSync {

    private PlayerSettingsSync() {}

    /** Announces that a player's settings were written, for {@code HeldPlayerState}'s write hook. */
    public static Consumer<UUID> announcer(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return player -> bus.publish(new PlayerSettingsChanged(bus.serverId(), player));
    }

    /** Registers {@code settings} to be read again on a peer's change. */
    public static void register(RemoteSyncRegistry registry, HeldSettings settings) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(settings, "settings");
        registry.register(message -> {
            if (message instanceof PlayerSettingsChanged changed) {
                settings.refresh(changed.player());
            }
        });
    }
}
