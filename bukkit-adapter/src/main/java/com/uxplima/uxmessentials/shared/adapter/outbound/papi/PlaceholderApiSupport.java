package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import me.clip.placeholderapi.PlaceholderAPI;
import org.jspecify.annotations.NullMarked;

/**
 * The single decision point for the PlaceholderAPI soft-depend. Every {@code me.clip.placeholderapi} symbol
 * is reached only through this class and only past {@link #isPresent()}, so a server without PlaceholderAPI
 * never resolves those classes and the plugin runs unchanged.
 *
 * <p>It does two things. {@link #registerExpansion} builds the {@link UxmEssentialsExpansion} over the wired
 * {@link PlaceholderContexts} and registers it, returning a handle whose {@link Registration#close()}
 * unregisters it on plugin disable. {@link #messageBridge} produces the pre-parse transform the {@code
 * BukkitMessageSink} applies before MiniMessage parses, so an operator's catalog line may embed {@code
 * %papi%} placeholders; without PlaceholderAPI the transform is the identity, a no-op.
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
     * Register the {@code uxmessentials} expansion over {@code contexts} when PlaceholderAPI is present and
     * at least one context contributed a seam. Returns empty (registering nothing) when PlaceholderAPI is
     * absent or no context is enabled, so the caller has nothing to close.
     */
    public static Optional<Registration> registerExpansion(PlaceholderContexts contexts, String version, Logger log) {
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(log, "log");
        if (!isPresent() || contexts.isEmpty()) {
            return Optional.empty();
        }
        UxmEssentialsExpansion expansion = new UxmEssentialsExpansion(new PlaceholderResolver(contexts), version);
        if (!expansion.register()) {
            log.warn("event=papi_expansion_register_failed identifier=uxmessentials");
            return Optional.empty();
        }
        log.info("event=papi_expansion_registered identifier=uxmessentials");
        return Optional.of(new Registration(expansion, log));
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
        OfflinePlayer player = Bukkit.getOfflinePlayer(viewer);
        return PlaceholderAPI.setPlaceholders(player, source);
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
        return source -> PlaceholderAPI.setPlaceholders((OfflinePlayer) null, source);
    }

    /** Whether {@code source} embeds a PlaceholderAPI token ({@code %...%}) the bridge would expand. */
    public static boolean hasPlaceholder(String source) {
        Objects.requireNonNull(source, "source");
        int open = source.indexOf('%');
        return open >= 0 && source.indexOf('%', open + 1) > open;
    }

    /** A registered expansion handle; {@link #close()} unregisters it on plugin disable. */
    public static final class Registration implements AutoCloseable {

        private final UxmEssentialsExpansion expansion;
        private final Logger log;

        private Registration(UxmEssentialsExpansion expansion, Logger log) {
            this.expansion = expansion;
            this.log = log;
        }

        @Override
        public void close() {
            expansion.unregister();
            log.info("event=papi_expansion_unregistered identifier=uxmessentials");
        }
    }
}
