package com.uxplima.uxmessentials.holograms.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.adapter.inbound.command.HologramCommands;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramClickListener;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramVisibilityListener;
import com.uxplima.uxmessentials.holograms.adapter.outbound.EventDrivenNpcLocator;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRefreshTask;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramTeleportAdapter;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramTextOverrides;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramViewers;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CenterHologram;
import com.uxplima.uxmessentials.holograms.application.CopyHologram;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.DescribeHologram;
import com.uxplima.uxmessentials.holograms.application.HologramNotifier;
import com.uxplima.uxmessentials.holograms.application.InsertHologramLine;
import com.uxplima.uxmessentials.holograms.application.LinkHologramToNpc;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.ManageHologramViewer;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.NearbyHolograms;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.RotateHologram;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramClickCommand;
import com.uxplima.uxmessentials.holograms.application.SetHologramLeaderboard;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.TeleportToHologram;
import com.uxplima.uxmessentials.holograms.application.UnlinkHologramFromNpc;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramTeleporter;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.persistence.holograms.HologramRepositories;
import com.uxplima.uxmessentials.persistence.npc.NpcRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.miniplaceholders.MiniPlaceholdersSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.npc.ChannelResolver;
import com.uxplima.uxmlib.npc.PacketSender;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import com.uxplima.uxmlib.packet.display.internal.NmsDisplayTextPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

    /** The smallest cadence the refresh timer fires at — one second, the floor a refresh interval rounds to. */
    private static final Duration REFRESH_BASE = Duration.ofSeconds(1);

    private static final int REFRESH_BASE_TICKS = 20;

    /** Build the holograms adapters and use cases, and spawn the stored holograms. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders leaderboards) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(leaderboards, "leaderboards");
        KernelPorts kernel = ctx.kernel();
        HologramRepository repository = HologramRepositories.cached(persistence);
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        // A hologram is one shared TextDisplay. Its broadcast text resolves placeholders server-globally (online,
        // time, TPS); the identity transform when PlaceholderAPI is absent, so a default server pays nothing. On
        // top of that base, a line embedding a placeholder additionally renders per viewer through the text-override
        // collaborator below — each viewer sees their own resolved values over the one shared entity.
        HologramTextOverrides textOverrides = new HologramTextOverrides(
                perViewerTextPackets(),
                PlaceholderApiSupport::messageBridge,
                MiniMessage.miniMessage(),
                MiniPlaceholdersSupport::globalResolver,
                kernel.log());
        HologramViewers viewers = new HologramViewers(plugin, kernel.permissions(), repository::manualViewers);
        // The NPC-link seam: a locator over the npc context's stored set kept current by the domain-event bus,
        // re-anchoring a linked hologram when its NPC moves or is removed. The locator reads the npc persistence
        // directly (the npc table ships in the persistence V38 baseline, always applied), so a hologram may link to
        // an NPC even with the npc module disabled — it simply sees no live moves in that case.
        NpcRepository npcRepository = NpcRepositories.cached(persistence);
        // Break the renderer↔locator construction cycle: the locator's re-anchor forwards through a holder the
        // renderer is written into once it exists, so a move event re-renders only the holograms linked to that NPC.
        RendererHolder rendererHolder = new RendererHolder();
        EventDrivenNpcLocator npcLocator =
                new EventDrivenNpcLocator(npcRepository, name -> rendererHolder.reanchor(name));
        HologramRenderer renderer = new HologramRenderer(
                plugin,
                manager,
                kernel.scheduler(),
                kernel.log(),
                PlaceholderApiSupport.globalBridge(),
                MiniPlaceholdersSupport::globalResolver,
                viewers,
                textOverrides,
                npcLocator,
                leaderboards);
        rendererHolder.set(renderer);
        InProcessDomainEventPublisher events = (InProcessDomainEventPublisher) kernel.events();
        Consumer<DomainEvent> npcSubscriber = npcLocator;
        events.subscribe(npcSubscriber);
        HologramNotifier notifier = new HologramNotifier(kernel.messages(), kernel.messageSink());
        HologramServices services = assemble(kernel, repository, renderer, notifier, npcLocator);
        spawnStored(repository, renderer);
        // A joining player must pick up the permission-gated holograms they qualify for at once, not after a
        // refresh tick; this listener re-evaluates only the gated holograms for that one player.
        plugin.getServer().getPluginManager().registerEvents(new HologramVisibilityListener(renderer), plugin);
        plugin.getServer().getPluginManager().registerEvents(new HologramClickListener(plugin, repository), plugin);
        AutoCloseable refreshTask = scheduleRefresh(kernel.scheduler(), repository, renderer);
        // The cached repository's all() is the authoritative in-memory set after the one warm load, so reading
        // names off it per keystroke is allocation-light and never touches the database — safe on the tick thread.
        java.util.function.Supplier<java.util.List<String>> hologramNames = () -> repository.all().stream()
                .map(hologram -> hologram.name().value())
                .toList();
        // The same warm-set read for NPC names, so /hologram linknpc tab-completes against the current NPCs.
        java.util.function.Supplier<java.util.List<String>> npcNames = () ->
                npcRepository.all().stream().map(npc -> npc.name().value()).toList();
        return new Wired(
                HologramCommands.all(services, kernel.messages(), hologramNames, npcNames),
                renderer,
                repository,
                refreshTask,
                events,
                npcSubscriber);
    }

    /**
     * A write-once holder for the renderer so the event-driven NPC locator (built before the renderer, since the
     * renderer needs it) can route a re-anchor back into the renderer once it exists. A re-anchor that somehow
     * arrives before the renderer is set is dropped — there is nothing rendered yet to move.
     */
    private static final class RendererHolder {

        private @Nullable HologramRenderer renderer;

        void set(HologramRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
        }

        void reanchor(String npcName) {
            HologramRenderer current = renderer;
            if (current != null) {
                current.reanchorLinkedTo(npcName);
            }
        }
    }

    /**
     * Build the lib per-viewer text-override packet port: the channel resolver finds each viewer's Netty channel,
     * the sender writes to it, and the NMS port builds the {@code ClientboundSetEntityDataPacket} that overrides
     * one viewer's copy of a shared display's text. The same stack the nametags and tablist contexts use; it is a
     * no-op send for a viewer whose channel cannot be resolved.
     */
    private static DisplayTextPackets perViewerTextPackets() {
        return new NmsDisplayTextPackets(new PacketSender(new ChannelResolver()));
    }

    private static AutoCloseable scheduleRefresh(
            Scheduler scheduler, HologramRepository repository, HologramRenderer renderer) {
        // A single global timer ticks every second and re-renders only the holograms whose interval is due, so
        // a server with no refreshing hologram pays just the empty iteration. The handle is closed on stop.
        HologramRefreshTask task = new HologramRefreshTask(repository::all, renderer::refresh, REFRESH_BASE_TICKS);
        return scheduler.repeatGlobal(task::tick, REFRESH_BASE, REFRESH_BASE);
    }

    private static HologramServices assemble(
            KernelPorts kernel,
            HologramRepository repository,
            HologramRenderer renderer,
            HologramNotifier notifier,
            LinkedNpcLocator npcLocator) {
        Clock clock = Clock.systemUTC();
        HologramTeleporter teleporter = new HologramTeleportAdapter(kernel.scheduler(), kernel.log());
        return new HologramServices(
                new CreateHologram(repository, renderer, notifier, kernel.events(), clock),
                new DeleteHologram(repository, renderer, notifier, kernel.events()),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, renderer, notifier),
                new SetHologramLine(repository, renderer, notifier),
                new InsertHologramLine(repository, renderer, notifier),
                new RemoveHologramLine(repository, renderer, notifier),
                new MoveHologram(repository, renderer, notifier),
                new CenterHologram(repository, renderer, notifier),
                new TeleportToHologram(repository, teleporter, notifier),
                new CopyHologram(repository, renderer, notifier),
                new RotateHologram(repository, renderer, notifier),
                new DescribeHologram(repository, notifier),
                new NearbyHolograms(repository, notifier),
                new SetHologramAppearance(repository, renderer, notifier),
                new SetHologramRefresh(repository, renderer, notifier),
                new SetHologramVisibility(repository, renderer, notifier),
                new ManageHologramViewer(repository, renderer, notifier),
                new SetHologramModel(repository, renderer, notifier),
                new SetHologramClickCommand(repository, renderer, notifier),
                new SetHologramLeaderboard(repository, renderer, notifier),
                new LinkHologramToNpc(repository, renderer, notifier, npcLocator),
                new UnlinkHologramFromNpc(repository, renderer, notifier));
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
     * @param repository the cached hologram repository the PAPI seam reads the server-wide count from
     * @param refreshTask the global refresh timer handle, cancelled on stop so no task outlives a disable
     * @param events the domain-event bus the npc-link locator subscribed to, dropped on stop
     * @param npcSubscriber the npc-link locator subscription, unsubscribed on stop so it does not outlive a reload
     */
    public record Wired(
            List<CommandRegistration> commands,
            HologramRenderer renderer,
            HologramRepository repository,
            AutoCloseable refreshTask,
            InProcessDomainEventPublisher events,
            Consumer<DomainEvent> npcSubscriber) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(refreshTask, "refreshTask");
            Objects.requireNonNull(events, "events");
            Objects.requireNonNull(npcSubscriber, "npcSubscriber");
        }

        /** Cancel the refresh timer, drop the npc-link subscription, and despawn every spawned hologram. */
        public void stop() {
            events.unsubscribe(npcSubscriber);
            closeQuietly(refreshTask);
            renderer.despawnAll();
        }

        private static void closeQuietly(@Nullable AutoCloseable task) {
            if (task == null) {
                return;
            }
            try {
                task.close();
            } catch (Exception cancellation) {
                // The repeating-task handle's cancel does not throw; close() only declares the checked type.
            }
        }
    }
}
