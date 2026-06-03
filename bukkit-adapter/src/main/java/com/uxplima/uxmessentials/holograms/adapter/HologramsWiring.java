package com.uxplima.uxmessentials.holograms.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.adapter.inbound.command.HologramCommands;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.HologramNotifier;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.persistence.holograms.HologramRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.hologram.HologramManager;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the holograms context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the uxmLib native-Display hologram API, and produces the Brigadier command the plugin registers.
 * This is the one place the holograms context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the
 * delegate, invalidate in the cache). The renderer holds a fresh {@link HologramManager}; the uxmLib lifecycle
 * listener is installed once here so per-player viewer caches stay honest. On wire, every stored hologram is
 * spawned (each on its own region thread through the kernel {@code Scheduler}) so a restart brings the
 * holograms back exactly as configured. On stop the {@code Wired} bundle despawns them all so no display
 * entity is orphaned across a reload.
 */
@NullMarked
public final class HologramsWiring {

    private HologramsWiring() {}

    /** Build the holograms adapters and use cases, and spawn the stored holograms. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        KernelPorts kernel = ctx.kernel();
        HologramRepository repository = HologramRepositories.cached(persistence);
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        HologramRenderer renderer = new HologramRenderer(manager, kernel.scheduler(), kernel.log());
        HologramNotifier notifier = new HologramNotifier(kernel.messages(), kernel.messageSink());
        HologramServices services = assemble(kernel, repository, renderer, notifier);
        spawnStored(repository, renderer);
        return new Wired(HologramCommands.all(services, kernel.messages()), renderer);
    }

    private static HologramServices assemble(
            KernelPorts kernel, HologramRepository repository, HologramRenderer renderer, HologramNotifier notifier) {
        Clock clock = Clock.systemUTC();
        return new HologramServices(
                new CreateHologram(repository, renderer, notifier, kernel.events(), clock),
                new DeleteHologram(repository, renderer, notifier, kernel.events()),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, renderer, notifier),
                new SetHologramLine(repository, renderer, notifier),
                new RemoveHologramLine(repository, renderer, notifier),
                new MoveHologram(repository, renderer, notifier));
    }

    private static void spawnStored(HologramRepository repository, HologramRenderer renderer) {
        // Each render hops onto the hologram's own region thread inside the renderer, so this is safe to call
        // straight from the enable path; a world that is not yet loaded is skipped with a warning there.
        for (Hologram hologram : repository.all()) {
            renderer.render(hologram);
        }
    }

    /**
     * Everything the holograms module contributes once wired: the single Brigadier command and the renderer
     * whose live display entities must be despawned on stop so a reload re-spawns cleanly.
     *
     * @param commands the Brigadier command registrations to publish
     * @param renderer the live-entity renderer, despawned on stop
     */
    public record Wired(List<CommandRegistration> commands, HologramRenderer renderer) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(renderer, "renderer");
        }

        /** Despawn every spawned hologram so no display entity is orphaned across a reload. */
        public void stop() {
            renderer.despawnAll();
        }
    }
}
