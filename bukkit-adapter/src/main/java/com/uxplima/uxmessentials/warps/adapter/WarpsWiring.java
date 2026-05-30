package com.uxplima.uxmessentials.warps.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.warps.WarpRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommands;
import com.uxplima.uxmessentials.warps.adapter.outbound.TeleportWarpAdapter;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import com.uxplima.uxmessentials.warps.application.WarpNotifier;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the warps context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the teleport context's engine, and produces the Brigadier command list the plugin registers.
 * This is the one place the warps context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the
 * delegate, invalidate in the cache). The teleporter delegates execution to the teleport context — warps
 * never re-implements movement — which is why the wiring receives the already-constructed
 * {@link TeleportEngine}. The per-warp cost soft-couples to the economy context: the {@link WarpEconomy}
 * seam is injected as an {@link Optional}, currently {@link Optional#empty()} because economy lands in P3,
 * so a priced warp's cost is recorded but not charged until that bridge is wired.
 */
@NullMarked
public final class WarpsWiring {

    private WarpsWiring() {}

    /** Build the warps adapters and use cases from {@code ctx}, the {@code persistence} DSL, and the engine. */
    public static Wired wire(ModuleContext ctx, Persistence persistence, TeleportEngine teleportEngine) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        KernelPorts kernel = ctx.kernel();
        WarpRepository repository = WarpRepositories.cached(persistence);
        WarpNotifier notifier = new WarpNotifier(kernel.messages(), kernel.messageSink());
        WarpTeleporter teleporter = new TeleportWarpAdapter(teleportEngine);
        WarpServices services = assemble(kernel, repository, notifier, teleporter);
        return new Wired(WarpCommands.all(services, kernel.messages()));
    }

    private static WarpServices assemble(
            KernelPorts kernel, WarpRepository repository, WarpNotifier notifier, WarpTeleporter teleporter) {
        // The economy seam stays empty until the economy context lands (P3); a recorded warp cost is then
        // charged through it, and is ignored gracefully while it is absent.
        Optional<WarpEconomy> economy = Optional.empty();
        WarpAccess access = new WarpAccess(kernel.permissions(), economy);
        Clock clock = Clock.systemUTC();
        return new WarpServices(
                new UseWarp(repository, access, teleporter, notifier),
                new SetWarp(repository, notifier, kernel.events(), clock),
                new DelWarp(repository, notifier, kernel.events()),
                new ListWarps(repository, kernel.permissions(), notifier),
                new WarpInfo(repository, notifier),
                new MoveWarp(repository, notifier),
                kernel.playerLookup());
    }

    /**
     * Everything the warps module contributes once wired: the Brigadier commands. The warps context holds
     * no repeating scheduled work and no in-memory store beyond the repository cache, so there is nothing
     * to drain on stop — the module's {@code stop()} clears its own bookkeeping and the cache expires.
     *
     * @param commands the Brigadier command registrations to publish
     */
    public record Wired(List<CommandRegistration> commands) {

        public Wired {
            commands = List.copyOf(commands);
        }
    }
}
