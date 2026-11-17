package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionUnregisterEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jspecify.annotations.NullMarked;

/**
 * The single decision point for the PlaceholderAPI soft-depend. Every {@code me.clip.placeholderapi} symbol
 * is reached only through this class and only past {@link #isPresent()}, so a server without PlaceholderAPI
 * never resolves those classes and the plugin runs unchanged.
 *
 * <p>It does two things. {@link #registerExpansion} builds the {@link UxmEssentialsExpansion}s over the wired
 * {@link PlaceholderContexts}, one per {@link PlaceholderRoute}, and registers them, returning a handle whose
 * {@link Registration#close()} unregisters them on plugin disable. {@link #messageBridge} produces the pre-parse
 * transform the {@code BukkitMessageSink} applies before MiniMessage parses, so an operator's catalog line may embed
 * {@code %papi%} placeholders; without PlaceholderAPI the transform is the identity, a no-op.
 */
@NullMarked
public final class PlaceholderApiSupport {

    private static final String PLUGIN_NAME = "PlaceholderAPI";

    private PlaceholderApiSupport() {}

    /** True when the PlaceholderAPI plugin is installed on this server. */
    public static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    /**
     * Register the {@code %uxm_essentials_<key>%} expansion, and the {@code %uxmessentials_<key>%} one that keeps the
     * older placeholders answering, over {@code contexts} when PlaceholderAPI is present and at least one context
     * contributed a seam. Returns empty (registering nothing) when PlaceholderAPI is absent, no context is enabled,
     * or neither identifier could be taken, so the caller has nothing to close.
     */
    public static Optional<Registration> registerExpansion(
            Plugin plugin, PlaceholderContexts contexts, String version, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(log, "log");
        if (!isPresent() || contexts.isEmpty()) {
            return Optional.empty();
        }
        PlaceholderResolver resolver = new PlaceholderResolver(contexts);
        List<UxmEssentialsExpansion> registered = new ArrayList<>();
        for (PlaceholderRoute route : List.of(PlaceholderRoute.current(), PlaceholderRoute.legacy(log))) {
            String identifier = route.identifier();
            Optional<String> holder = foreignHolder(identifier);
            if (holder.isPresent()) {
                // PlaceholderAPI keeps one expansion per identifier and a new registration evicts the old one, so
                // taking an identifier another plugin holds would silently break that plugin's placeholders.
                log.warn("event=papi_expansion_identifier_taken identifier={} holder={}", identifier, holder.get());
                continue;
            }
            UxmEssentialsExpansion expansion = new UxmEssentialsExpansion(resolver, version, route);
            if (expansion.register()) {
                log.info("event=papi_expansion_registered identifier={}", identifier);
                registered.add(expansion);
            } else {
                log.warn("event=papi_expansion_register_failed identifier={}", identifier);
            }
        }
        if (registered.isEmpty()) {
            return Optional.empty();
        }
        Registration registration = new Registration(registered, log);
        registration.listen(plugin);
        return Optional.of(registration);
    }

    /** The author of an expansion that is not ours and already holds {@code identifier}, or empty when none does. */
    private static Optional<String> foreignHolder(String identifier) {
        return holder(identifier)
                .filter(expansion -> !(expansion instanceof UxmEssentialsExpansion))
                .map(expansion -> expansion.getName() + " by " + expansion.getAuthor());
    }

    /** The expansion PlaceholderAPI asks for {@code identifier} right now, whoever registered it. */
    private static Optional<PlaceholderExpansion> holder(String identifier) {
        return PlaceholderAPIPlugin.getInstance().getLocalExpansionManager().findExpansionByIdentifier(identifier);
    }

    /**
     * The pre-parse transform the message sink applies to a rendered template for {@code viewer} before
     * MiniMessage parses it: {@code PlaceholderAPI.setPlaceholders(viewer, source)} when PlaceholderAPI is
     * present, the identity otherwise. The viewer is resolved from the uuid at call time so an offline
     * viewer (a console/system delivery) is a silent identity transform.
     */
    public static UnaryOperator<String> messageBridge(java.util.UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        return source -> expand(viewer, source);
    }

    private static String expand(java.util.UUID viewer, String source) {
        return expand(viewer, source, PlaceholderAPI::setPlaceholders);
    }

    /**
     * {@code source} with its placeholders filled by {@code fill}, or {@code source} itself when it holds none:
     * PlaceholderAPI scans every text it is handed with a regular expression, and most HUD lines carry no
     * placeholder. Package-private: a test supplies the filler.
     */
    static String expand(java.util.UUID viewer, String source, BiFunction<OfflinePlayer, String, String> fill) {
        if (!hasPlaceholder(source)) {
            return source;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(viewer);
        // A value's % is kept out of PlaceholderAPI's sight for the call, so it can neither open nor close a
        // placeholder.
        return PlaceholderShield.around(text -> fill.apply(player, text)).apply(source);
    }

    /**
     * The transform that fills the relational placeholders ({@code %rel_...%}) of a text drawn for {@code viewer}
     * about {@code target}: a chat line or a tab row is drawn once for every reader, so a value that depends on who
     * reads it belongs there. The identity when PlaceholderAPI is absent, when the text holds no relational
     * placeholder, or when either player is not online, because the relation needs both.
     */
    public static UnaryOperator<String> relationalBridge(java.util.UUID viewer, java.util.UUID target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        return source -> {
            if (!hasRelational(PlaceholderShield.withoutValues(source))) {
                return source;
            }
            Player one = Bukkit.getPlayer(viewer);
            Player two = Bukkit.getPlayer(target);
            return one == null || two == null
                    ? source
                    : PlaceholderShield.around(text -> PlaceholderAPI.setRelationalPlaceholders(one, two, text))
                            .apply(source);
        };
    }

    /** Whether {@code source} holds a relational placeholder, whose value depends on who reads the text. */
    public static boolean hasRelational(String source) {
        Objects.requireNonNull(source, "source");
        return source.contains("%rel_");
    }

    /**
     * A viewer-free pre-parse transform for text shown to everyone at once (a shared hologram line is one
     * entity, not one per player). It resolves server-global placeholders ({@code %server_online%}, time,
     * TPS, …) against a {@code null} player and leaves player-relative tokens for the operator to avoid;
     * the identity transform when PlaceholderAPI is absent. Per-player-relative resolution would need a
     * per-viewer hologram, which the shared-text renderer does not spawn.
     */
    public static UnaryOperator<String> globalBridge() {
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        UnaryOperator<String> fill =
                PlaceholderShield.around(text -> PlaceholderAPI.setPlaceholders((OfflinePlayer) null, text));
        return source -> hasPlaceholder(source) ? fill.apply(source) : source;
    }

    /**
     * Whether {@code source} embeds a PlaceholderAPI token ({@code %...%}) the bridge would expand. A value's marked
     * {@code %} (see {@link PlaceholderShield}) does not count.
     */
    public static boolean hasPlaceholder(String source) {
        Objects.requireNonNull(source, "source");
        String unmarked = PlaceholderShield.withoutValues(source);
        int open = unmarked.indexOf('%');
        return open >= 0 && unmarked.indexOf('%', open + 1) > open;
    }

    /**
     * The registered expansions; {@link #close()} unregisters them on plugin disable.
     *
     * <p>PlaceholderAPI removes an expansion by its identifier, not by the object, so an expansion of ours that another
     * plugin has since replaced is left alone on close: unregistering it would take that plugin's expansion down.
     * While open it also watches for one of ours being dropped by someone else, which would otherwise only show as
     * placeholders that suddenly read as written.
     */
    public static final class Registration implements AutoCloseable {

        private final List<UxmEssentialsExpansion> expansions;
        private final Logger log;
        private final AtomicBoolean closing = new AtomicBoolean();
        private final Listener evictions = new Evictions(this);

        private Registration(List<UxmEssentialsExpansion> expansions, Logger log) {
            this.expansions = List.copyOf(expansions);
            this.log = log;
        }

        private void listen(Plugin plugin) {
            Bukkit.getPluginManager().registerEvents(evictions, plugin);
        }

        /** Whether {@code %uxm_essentials_<key>%} answers right now, rather than only the older form. */
        public boolean answersCurrentForm() {
            String identifier = PlaceholderRoute.current().identifier();
            return expansions.stream()
                    .anyMatch(expansion -> expansion.getIdentifier().equals(identifier) && held(expansion));
        }

        /** Whether any expansion of ours still answers. */
        public boolean answersAny() {
            return expansions.stream().anyMatch(Registration::held);
        }

        private static boolean held(UxmEssentialsExpansion expansion) {
            return holder(expansion.getIdentifier())
                    .filter(current -> current == expansion)
                    .isPresent();
        }

        private void dropped(PlaceholderExpansion expansion) {
            if (closing.get() || expansions.stream().noneMatch(ours -> ours == expansion)) {
                return;
            }
            log.warn(
                    "event=papi_expansion_evicted identifier={} note=another plugin or PlaceholderAPI dropped it, so"
                            + " these placeholders read as written until the plugin is enabled again",
                    expansion.getIdentifier());
        }

        @Override
        public void close() {
            closing.set(true);
            HandlerList.unregisterAll(evictions);
            for (UxmEssentialsExpansion expansion : expansions) {
                if (!held(expansion)) {
                    log.info("event=papi_expansion_not_ours identifier={}", expansion.getIdentifier());
                    continue;
                }
                expansion.unregister();
                log.info("event=papi_expansion_unregistered identifier={}", expansion.getIdentifier());
            }
        }
    }

    /** Hears every expansion PlaceholderAPI drops and hands it to the registration that may own it. */
    static final class Evictions implements Listener {

        private final Registration registration;

        private Evictions(Registration registration) {
            this.registration = registration;
        }

        @EventHandler
        public void onUnregister(ExpansionUnregisterEvent event) {
            registration.dropped(event.getExpansion());
        }
    }
}
