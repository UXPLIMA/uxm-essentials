package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The language each online player's client reads, known on any thread.
 *
 * <p>{@code Player.locale()} may only be asked on the thread that owns the player, and a line written after a
 * database read is written on another. So the answer is taken where it may be asked, when a player joins and when
 * their client switches, and kept here. Before this, such a line found no language for its reader and was written
 * in the server's default: a Turkish player's {@code /balance} arrived in English.
 */
@NullMarked
public final class ClientLocales {

    private final ConcurrentHashMap<UUID, Locale> known = new ConcurrentHashMap<>();

    /** Record the language this player's client reads now. */
    public void remember(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        known.compute(player, (id, before) -> locale);
    }

    /** Forget a player who left. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        known.compute(player, (id, before) -> null);
    }

    /** The language this player's client reads, when they are online. */
    public Optional<Locale> of(UUID player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(known.get(player));
    }
}
