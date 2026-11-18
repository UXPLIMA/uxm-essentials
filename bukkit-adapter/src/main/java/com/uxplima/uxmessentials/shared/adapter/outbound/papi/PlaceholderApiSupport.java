package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.hook.placeholder.SharedPlaceholders;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.events.ExpansionUnregisterEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single decision point for the PlaceholderAPI soft-depend. Every {@code me.clip.placeholderapi} symbol
 * is reached only through this class and only past {@link #isPresent()}, so a server without PlaceholderAPI
 * never resolves those classes and the plugin runs unchanged.
 *
 * <p>It does two things. {@link #registerExpansion} publishes the wired {@link PlaceholderContexts}: it joins the
 * {@code uxm} expansion every UXM plugin shares with the prefix {@code essentials}, and registers the older
 * {@link UxmEssentialsExpansion}, returning a handle whose {@link Registration#close()} takes both down on plugin
 * disable. {@link #markupBridge} produces the pre-parse transform a line goes through before MiniMessage parses it,
 * so an operator's catalog line may embed {@code %papi%} placeholders; {@link #valueBridge} fills a text that is read
 * as a value, such as a requirement. Without PlaceholderAPI both are the identity, a no-op.
 *
 * <p>The two differ only in our own values. A markup bridge fills inside a {@link MarkupPass}, where a value our
 * expansion answers goes in as the words it holds, so a name a player chose cannot become markup in our line; a value
 * bridge, and every other plugin, gets our values as they are.
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
     * Answer {@code %uxm_essentials_<key>%} through the shared {@code uxm} expansion, and register the
     * {@code %uxmessentials_<key>%} one that keeps the older placeholders answering, over {@code contexts} when
     * PlaceholderAPI is present and at least one context contributed a seam. Returns empty (publishing nothing) when
     * PlaceholderAPI is absent, no context is enabled, or neither form could be published, so the caller has nothing to
     * close.
     *
     * <p>uxmLib registers the shared expansion when no UXM plugin has yet, and keeps it standing when the plugin that
     * registered it goes. A prefix another plugin holds, or a {@code uxm} held by an expansion that is not the shared
     * one, is refused: uxmLib's line in the log says which, and only the older form answers.
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
        EssentialsAnswers answers = new EssentialsAnswers(new PlaceholderResolver(contexts));
        String prefix = PlaceholderRoute.sharedPrefix();
        Optional<Runnable> leave = SharedPlaceholders.join(plugin, prefix, answers.registry());
        if (leave.isPresent()) {
            log.info("event=papi_expansion_joined identifier={} prefix={}", SharedPlaceholders.IDENTIFIER, prefix);
        } else {
            log.warn(
                    "event=papi_expansion_not_joined identifier={} prefix={} note=the line before this one says why",
                    SharedPlaceholders.IDENTIFIER,
                    prefix);
        }
        List<UxmEssentialsExpansion> registered = new ArrayList<>();
        PlaceholderRoute legacy = PlaceholderRoute.legacy(log);
        String identifier = legacy.identifier();
        Optional<String> holder = foreignHolder(identifier);
        if (holder.isPresent()) {
            // PlaceholderAPI keeps one expansion per identifier and a new registration evicts the old one, so
            // taking an identifier another plugin holds would silently break that plugin's placeholders.
            log.warn("event=papi_expansion_identifier_taken identifier={} holder={}", identifier, holder.get());
        } else {
            UxmEssentialsExpansion expansion = new UxmEssentialsExpansion(answers, version, legacy);
            if (expansion.register()) {
                log.info("event=papi_expansion_registered identifier={}", identifier);
                registered.add(expansion);
            } else {
                log.warn("event=papi_expansion_register_failed identifier={}", identifier);
            }
        }
        if (leave.isEmpty() && registered.isEmpty()) {
            return Optional.empty();
        }
        Registration registration = new Registration(plugin.getName(), prefix, leave.orElse(null), registered, log);
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
     * The transform a line drawn for {@code viewer} goes through before MiniMessage parses it:
     * {@code PlaceholderAPI.setPlaceholders(viewer, source)} in a {@link MarkupPass} when PlaceholderAPI is present,
     * the identity otherwise. The viewer is resolved from the uuid at call time so an offline viewer (a console or
     * system delivery) is a silent identity transform.
     */
    public static UnaryOperator<String> markupBridge(java.util.UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        return markupBridge(viewer, PlaceholderAPI::setPlaceholders);
    }

    /** {@link #markupBridge(java.util.UUID)} over {@code fill}. Package-private: a test supplies the filler. */
    static UnaryOperator<String> markupBridge(java.util.UUID viewer, BiFunction<OfflinePlayer, String, String> fill) {
        return source -> MarkupPass.during(() -> expand(viewer, source, fill));
    }

    /**
     * The transform for a text read as a value rather than shown, such as either side of a requirement or a balance
     * a backend reads back: the same fill as {@link #markupBridge}, with every value as it is.
     */
    public static UnaryOperator<String> valueBridge(java.util.UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        return valueBridge(viewer, PlaceholderAPI::setPlaceholders);
    }

    /** {@link #valueBridge(java.util.UUID)} over {@code fill}. Package-private: a test supplies the filler. */
    static UnaryOperator<String> valueBridge(java.util.UUID viewer, BiFunction<OfflinePlayer, String, String> fill) {
        return source -> expand(viewer, source, fill);
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
     * placeholder, or when either player is not online, because the relation needs both. It fills in a
     * {@link MarkupPass}, since both surfaces parse the result.
     */
    public static UnaryOperator<String> relationalMarkupBridge(java.util.UUID viewer, java.util.UUID target) {
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
                    : MarkupPass.during(() -> PlaceholderShield.around(
                                    text -> PlaceholderAPI.setRelationalPlaceholders(one, two, text))
                            .apply(source));
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
     * per-viewer hologram, which the shared-text renderer does not spawn. It fills in a {@link MarkupPass}.
     */
    public static UnaryOperator<String> globalMarkupBridge() {
        if (!isPresent()) {
            return UnaryOperator.identity();
        }
        UnaryOperator<String> fill =
                PlaceholderShield.around(text -> PlaceholderAPI.setPlaceholders((OfflinePlayer) null, text));
        return source -> hasPlaceholder(source) ? MarkupPass.during(() -> fill.apply(source)) : source;
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
     * What was published: our place in the shared {@code uxm} expansion and the older expansion of our own.
     * {@link #close()} leaves the one and unregisters the other on plugin disable.
     *
     * <p>PlaceholderAPI removes an expansion by its identifier, not by the object, so an older expansion of ours that
     * another plugin has since replaced is left alone on close: unregistering it would take that plugin's expansion
     * down. While open it also watches for that expansion being dropped by someone else, which would otherwise only
     * show as placeholders that suddenly read as written. The shared expansion is not ours to watch: uxmLib hands it
     * from one plugin to the next as they come and go, and PlaceholderAPI reports each hand-over as a drop.
     */
    public static final class Registration implements AutoCloseable {

        private final String pluginName;
        private final String prefix;
        private final @Nullable Runnable leave;
        private final List<UxmEssentialsExpansion> expansions;
        private final Logger log;
        private final AtomicBoolean closing = new AtomicBoolean();
        private final Listener evictions = new Evictions(this);

        private Registration(
                String pluginName,
                String prefix,
                @Nullable Runnable leave,
                List<UxmEssentialsExpansion> expansions,
                Logger log) {
            this.pluginName = pluginName;
            this.prefix = prefix;
            this.leave = leave;
            this.expansions = List.copyOf(expansions);
            this.log = log;
        }

        private void listen(Plugin plugin) {
            Bukkit.getPluginManager().registerEvents(evictions, plugin);
        }

        /**
         * Whether {@code %uxm_essentials_<key>%} answers right now, rather than only the older form: we joined, and the
         * expansion PlaceholderAPI holds as {@code uxm} still has our prefix as ours. The expansion gives its member
         * table as a JDK {@link Supplier} of a {@link Map}, whose members name the plugin that joined, which is the
         * contract every UXM plugin shares whatever copy of uxmLib it carries.
         */
        public boolean answersCurrentForm() {
            if (leave == null || closing.get()) {
                return false;
            }
            return holder(SharedPlaceholders.IDENTIFIER)
                    .filter(shared -> shared instanceof Supplier<?> members
                            && members.get() instanceof Map<?, ?> table
                            && table.get(prefix) instanceof PlaceholderExpansion member
                            && pluginName.equalsIgnoreCase(member.getRequiredPlugin()))
                    .isPresent();
        }

        /** Whether either form of ours still answers. */
        public boolean answersAny() {
            return answersCurrentForm() || expansions.stream().anyMatch(Registration::held);
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
            if (closing.getAndSet(true)) {
                return;
            }
            HandlerList.unregisterAll(evictions);
            if (leave != null) {
                leave.run();
                log.info("event=papi_expansion_left identifier={} prefix={}", SharedPlaceholders.IDENTIFIER, prefix);
            }
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
