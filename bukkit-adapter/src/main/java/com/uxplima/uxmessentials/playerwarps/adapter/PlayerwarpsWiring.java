package com.uxplima.uxmessentials.playerwarps.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommands;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener.PlayerwarpsJoinListener;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.TeleportPlayerWarpAdapter;
import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the player-warps context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the teleport context's engine, and produces the Brigadier command list the plugin registers. This
 * is the one place the player-warps context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator keyed by owner (write-through
 * at the delegate, invalidate in the cache). The teleporter delegates execution to the teleport context —
 * player-warps never re-implements movement — which is why the wiring receives the already-constructed
 * {@link TeleportEngine}. The per-owner count limit resolves through {@link PlayerWarpQuota} over the shared
 * {@code Permissions} reducer with the module's {@code default-limit} config value as the fallback.
 */
@NullMarked
public final class PlayerwarpsWiring {

    private static final int DEFAULT_LIMIT = 3;

    private PlayerwarpsWiring() {}

    /**
     * Build the player-warps adapters and use cases over the kernel ports and the teleport engine. The warp
     * arrival-notification registry is shared from the warps module so a player-warp hop fires the same welcome
     * effects; when warps is disabled it is {@code null} and player-warps falls back to a private throwaway
     * registry (no listener consumes it, exactly as before this was injected).
     */
    public static Wired wire(
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpRepositoryHandle
                    playerWarpHandle,
            com.uxplima.uxmessentials.warps.adapter.@org.jspecify.annotations.Nullable WarpTeleportRegistry
                    teleportRegistry) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        KernelPorts kernel = ctx.kernel();
        PlayerWarpRepository repository = PlayerWarpRepositories.cached(persistence);
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(kernel.messages(), kernel.messageSink());
        com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry registry = teleportRegistry != null
                ? teleportRegistry
                : new com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry();
        PlayerWarpTeleporter teleporter = new TeleportPlayerWarpAdapter(teleportEngine, registry);
        PlayerWarpQuota quota = new PlayerWarpQuota(kernel.permissions(), defaultLimit(ctx));
        if (playerWarpHandle != null) {
            playerWarpHandle.bind(repository);
        }
        PlayerWarpServices services = assemble(kernel, repository, notifier, teleporter, quota, editorView, ctx);
        PlayerwarpsJoinListener joinWarmer = new PlayerwarpsJoinListener(repository, kernel.scheduler());
        return new Wired(PlayerWarpCommands.all(services, kernel.messages()), List.of(joinWarmer), repository, quota);
    }

    private static PlayerWarpServices assemble(
            KernelPorts kernel,
            PlayerWarpRepository repository,
            PlayerWarpNotifier notifier,
            PlayerWarpTeleporter teleporter,
            PlayerWarpQuota quota,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.shared.application.module.ModuleContext ctx) {
        Clock clock = Clock.systemUTC();
        return new PlayerWarpServices(
                new SetPlayerWarp(
                        repository,
                        quota,
                        notifier,
                        kernel.events(),
                        clock,
                        ctx.config().getStringList("world-blacklist", List.of())),
                new DelPlayerWarp(repository, notifier, kernel.events()),
                new UsePlayerWarp(
                        repository,
                        teleporter,
                        notifier,
                        new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                        kernel.permissions()),
                new ListPlayerWarps(repository, notifier),
                new SetPlayerWarpVisibility(repository, notifier),
                kernel.playerLookup(),
                repository,
                editorView,
                kernel.scheduler());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_LIMIT));
    }

    /**
     * Everything the player-warps module contributes once wired: the Brigadier commands, the join cache-warmer,
     * and the read ports the PAPI seam queries. The context holds no repeating scheduled work and no in-memory
     * store beyond the repository cache, so there is nothing to drain on stop — the module's {@code stop()}
     * clears its own bookkeeping and the cache expires.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the cached player-warp repository the PAPI seam reads owned warps from
     * @param quota the per-owner count-limit reducer the PAPI seam reads the limit through
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PlayerWarpRepository repository,
            PlayerWarpQuota quota) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
        }
    }
}
