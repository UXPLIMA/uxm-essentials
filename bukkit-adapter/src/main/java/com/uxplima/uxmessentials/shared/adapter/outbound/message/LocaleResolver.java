package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a viewer's locale through the deterministic fallback chain. The first hit
 * wins:
 *
 * <ol>
 *   <li>the language the server forces, {@code language.force}, which nobody's choice overrides;</li>
 *   <li>the player's own choice, made with {@code /lang} here or in any other plugin of ours, which the
 *       {@link LocaleStore} reads from the player's own data;</li>
 *   <li>when the server follows the client, {@code language.follow-client}: the locale bound for this request
 *       at the command boundary ({@link LocaleScope#CURRENT}), carried across async hops so a deferred
 *       message resolves in the requester's language on a worker thread;</li>
 *   <li>when the server follows the client: the language the viewer's own client reads, which
 *       {@link ClientLocales} knows for every online player on any thread. A line written after a hop off the
 *       command's thread, or with no command behind it at all, finds its reader's language here; before this
 *       step it found the server's default, and a Turkish player's {@code /balance} arrived in English;</li>
 *   <li>the configured default, {@code language.default}.</li>
 * </ol>
 *
 * <p>A server with one audience turns {@code follow-client} off. Its German players whose clients read English
 * then read German, and a player who wants another language chooses it. The request scope is skipped too,
 * because what it carries is what the client said.
 *
 * <p>The command boundary resolves through {@link #resolveAt}, with the client's language it read on the
 * player's own thread, so the scope it binds follows the same rules.
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
    private final LanguageSettings settings;

    /** A resolver that follows the client and forces nothing, with {@code serverDefault} as the default. */
    public LocaleResolver(LocaleStore overrides, ClientLocales clients, Locale serverDefault) {
        this(overrides, clients, LanguageSettings.following(Objects.requireNonNull(serverDefault, "serverDefault")));
    }

    public LocaleResolver(LocaleStore overrides, ClientLocales clients, LanguageSettings settings) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.clients = Objects.requireNonNull(clients, "clients");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** The default, follow-client and forced language this resolver was built with. */
    public LanguageSettings settings() {
        return settings;
    }

    /** The clients' languages this resolver reads, for the listener that keeps them current. */
    public ClientLocales clients() {
        return clients;
    }

    /** The viewer's resolved locale, on any thread. */
    public Locale resolve(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Optional<Locale> chosen = settings.forcedLocale().or(() -> overrides.override(viewer));
        if (chosen.isPresent()) {
            return chosen.get();
        }
        if (!settings.followClient()) {
            return settings.defaultLocale();
        }
        return LocaleScope.orElse(clients.of(viewer.uuid()).orElse(settings.defaultLocale()));
    }

    /** Whether this viewer's language follows their client: nothing forced, nothing chosen, the client followed. */
    public boolean readsTheClient(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return settings.followClient()
                && settings.forcedLocale().isEmpty()
                && overrides.override(viewer).isEmpty();
    }

    /**
     * The viewer's locale at the command boundary, where the client's language was just read on the player's own
     * thread. A client that has not said yet reads as no language at all.
     */
    public Locale resolveAt(PlayerRef viewer, Locale client) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(client, "client");
        Optional<Locale> chosen = settings.forcedLocale().or(() -> overrides.override(viewer));
        if (chosen.isPresent()) {
            return chosen.get();
        }
        boolean heard = !client.getLanguage().isEmpty();
        return settings.followClient() && heard ? client : settings.defaultLocale();
    }
}
