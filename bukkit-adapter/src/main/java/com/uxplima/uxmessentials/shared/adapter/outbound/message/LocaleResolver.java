package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a viewer's locale through the deterministic fallback chain (docs/13-i18n §4). The first hit
 * wins:
 *
 * <ol>
 *   <li>the player's persisted {@code /lang} override (from the {@link LocaleStore}): an operator's
 *       explicit choice always beats the client;</li>
 *   <li>the locale bound for this request at the command boundary ({@link LocaleScope#CURRENT}), the
 *       client locale captured on the region thread and carried across async hops, so a deferred
 *       message resolves in the requester's language on a worker thread;</li>
 *   <li>the language the viewer's own client reads, which {@link ClientLocales} knows for every online player on
 *       any thread. A line written after a hop off the command's thread, or with no command behind it at all,
 *       finds its reader's language here; before this step it found the server's default, and a Turkish
 *       player's {@code /balance} arrived in English;</li>
 *   <li>the configured server-default locale, for a viewer the server has not heard from;</li>
 *   <li>{@link Locale#ENGLISH}, the canonical root.</li>
 * </ol>
 *
 * <p>The resolver never touches the Bukkit API: the live client locale ({@code Player.locale()}) is a
 * region-thread call captured once at the boundary and folded into {@link LocaleScope}, so this class
 * works on any worker thread. The per-key {@code en} fallback (a key missing in {@code tr} falling back
 * to {@code en} for that key only) lives one layer down in the {@code LocaleCatalog}; this class only
 * chooses which locale to ask for.
 */
@NullMarked
public final class LocaleResolver {

    private final LocaleStore overrides;
    private final ClientLocales clients;
    private final Locale serverDefault;

    public LocaleResolver(LocaleStore overrides, ClientLocales clients, Locale serverDefault) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.serverDefault = Objects.requireNonNull(serverDefault, "serverDefault");
    }

    /** The clients' languages this resolver reads, for the listener that keeps them current. */
    public ClientLocales clients() {
        return clients;
    }

    /** The viewer's resolved locale, walking the override, scope, client, server-default chain. */
    public Locale resolve(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Optional<Locale> override = overrides.override(viewer);
        if (override.isPresent()) {
            return override.get();
        }
        return LocaleScope.orElse(clients.of(viewer.uuid()).orElse(serverDefault));
    }
}
