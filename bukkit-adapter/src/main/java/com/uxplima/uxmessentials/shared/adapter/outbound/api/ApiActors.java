package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The plugin behind an API write, as the use cases see it.
 *
 * <p>Use cases take a {@code PlayerRef} for whoever is acting, because until now that was always a player or the
 * console. A plugin is neither, so it gets a ref named after itself and keyed by a UUID derived from that name:
 * stable across restarts, so an audit trail groups by plugin, and not a UUID any account could hold.
 *
 * <p>Feedback addressed to the actor lands nowhere, which is what we want. Nobody is holding that UUID, and the
 * message sink drops a message to a player who is not here, so the plugin's own audit line is written while the
 * "you gave 50 coins to Alice" line goes to no one.
 */
@NullMarked
public final class ApiActors {

    private ApiActors() {}

    /** The acting ref for a write attributed to {@code source}, which is the calling plugin's name. */
    public static PlayerRef of(String source) {
        Objects.requireNonNull(source, "source");
        UUID id = UUID.nameUUIDFromBytes(("uxmessentials:api:" + source).getBytes(StandardCharsets.UTF_8));
        return new PlayerRef(id, source);
    }
}
