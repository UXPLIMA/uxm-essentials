package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link DiscordlinkPlaceholders} over the discord-link context's DB-backed {@link DiscordLinkStore}. Built
 * during discordlink wiring from the same store the {@code /discordlink} and {@code /discordunlink} use cases
 * hold, so a placeholder matches the binding the player redeemed.
 *
 * <p>The store is keyed by the account uuid and lives in the host persistence (not the optional Discord bridge
 * jar), so both reads answer for an offline player. The binding carries only the Discord snowflake id, which
 * {@link #discordId(PlayerRef)} returns as its plain string.
 */
@NullMarked
public final class StoreDiscordlinkPlaceholders implements DiscordlinkPlaceholders {

    private final DiscordLinkStore store;

    public StoreDiscordlinkPlaceholders(DiscordLinkStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean linked(PlayerRef who) {
        return store.findByPlayer(Objects.requireNonNull(who, "who").uuid()).isPresent();
    }

    @Override
    public Optional<String> discordId(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return store.findByPlayer(who.uuid()).map(ConfirmedLink::discordId).map(id -> id.value());
    }
}
