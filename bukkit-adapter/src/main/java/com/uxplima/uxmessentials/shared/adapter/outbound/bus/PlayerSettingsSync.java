package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.network.PlayerSettingsChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the per-player settings every backend shares current across the network. A change written here is announced
 * in a {@link PlayerSettingsChanged}; a backend holding that player reads their settings again. That covers a player
 * who reached the next backend before the change was written; the safety timer reads every held player again for a
 * frame that was lost.
 */
@NullMarked
public final class PlayerSettingsSync {

    private PlayerSettingsSync() {}

    /** Announces that a player's settings were written, for {@code HeldPlayerState}'s write hook. */
    public static Consumer<UUID> announcer(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return player -> bus.publish(new PlayerSettingsChanged(bus.serverId(), player));
    }

    /** Registers {@code settings} to be read again on a peer's change and on the safety timer. */
    public static void register(RemoteSyncRegistry registry, HeldSettings settings) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(settings, "settings");
        registry.register(message -> {
            if (message instanceof PlayerSettingsChanged changed) {
                settings.refresh(changed.player());
            }
        });
        registry.registerResync(settings::refreshAll);
    }
}
