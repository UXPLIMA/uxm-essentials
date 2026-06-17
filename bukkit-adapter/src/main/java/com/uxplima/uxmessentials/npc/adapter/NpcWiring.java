package com.uxplima.uxmessentials.npc.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcCommand;
import com.uxplima.uxmessentials.npc.adapter.inbound.command.NpcSkinByName;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcInteractionListener;
import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcLifecycleListener;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcActionRunner;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcCommandRunner;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitServerConnector;
import com.uxplima.uxmessentials.npc.adapter.outbound.CompositeSkinService;
import com.uxplima.uxmessentials.npc.adapter.outbound.HttpClientFetcher;
import com.uxplima.uxmessentials.npc.adapter.outbound.MineSkinService;
import com.uxplima.uxmessentials.npc.adapter.outbound.MojangSkinService;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcActionRunner;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import com.uxplima.uxmessentials.npc.adapter.outbound.NpcViewSpawner;
import com.uxplima.uxmessentials.npc.application.AddNpcAction;
import com.uxplima.uxmessentials.npc.application.CenterNpc;
import com.uxplima.uxmessentials.npc.application.ClearNpcActions;
import com.uxplima.uxmessentials.npc.application.CopyNpc;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.FixNpc;
import com.uxplima.uxmessentials.npc.application.ListNpcActions;
import com.uxplima.uxmessentials.npc.application.ListNpcTypeData;
import com.uxplima.uxmessentials.npc.application.ListNpcs;
import com.uxplima.uxmessentials.npc.application.MoveNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.NpcNotifier;
import com.uxplima.uxmessentials.npc.application.NpcSettings;
import com.uxplima.uxmessentials.npc.application.RemoveNpcAction;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcCollidable;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcEntityType;
import com.uxplima.uxmessentials.npc.application.SetNpcEquipment;
import com.uxplima.uxmessentials.npc.application.SetNpcGlowing;
import com.uxplima.uxmessentials.npc.application.SetNpcInteractionCooldown;
import com.uxplima.uxmessentials.npc.application.SetNpcLookAtPlayer;
import com.uxplima.uxmessentials.npc.application.SetNpcMirrorSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcPose;
import com.uxplima.uxmessentials.npc.application.SetNpcRange;
import com.uxplima.uxmessentials.npc.application.SetNpcScale;
import com.uxplima.uxmessentials.npc.application.SetNpcShowInTab;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.application.SetNpcSkinSlim;
import com.uxplima.uxmessentials.npc.application.SetNpcState;
import com.uxplima.uxmessentials.npc.application.SetNpcTypeData;
import com.uxplima.uxmessentials.npc.application.port.NpcEconomy;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.persistence.npc.NpcRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.npc.ChannelResolver;
import com.uxplima.uxmlib.npc.PacketSender;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.internal.NmsNpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the npc context's adapters and use cases over the injected kernel ports, the persistence DSL, and
 * the uxmLib NPC packet stack, and produces the Brigadier command and lifecycle listener the plugin registers.
 * This is the one place the npc context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator. The renderer holds the packet
 * stack — a {@link ChannelResolver} → {@link PacketSender} → {@link NmsNpcPackets} — and sends each viewer a
 * fake-player spawn (then hides its tab entry) with no real entity. On wire every stored NPC is spawned to the
 * online viewers in range; a global refresh timer re-evaluates range each second so an NPC appears/disappears as
 * players move, and a faster look timer turns each looking NPC toward its nearby viewers. The interaction
 * listener runs an NPC's bound click command when a player clicks its fake entity. A {@code COST} click action
 * charges through the optional {@link NpcEconomy} bridge captured during economy wiring — empty on a server
 * without economy, in which case the cost gate is simply skipped. On stop the {@code Wired} bundle cancels both
 * timers and removes every shown NPC from every viewer so nothing is orphaned across a reload.
 */
@NullMarked
public final class NpcWiring {

    private static final Duration REFRESH_PERIOD = Duration.ofSeconds(1);
    /** The radius within which a viewer is shown an NPC — the vanilla player tracking range. */
    private static final double RENDER_RANGE = 48.0;
    /** How long after the spawn the tab entry is hidden, so the client links the skin before it goes. */
    private static final Duration TAB_HIDE_DELAY = Duration.ofSeconds(1);

    private NpcWiring() {}

    /** Build the npc adapters and use cases, and spawn the stored NPCs to the online viewers in range. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, Optional<NpcEconomy> economy) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(economy, "economy");
        KernelPorts kernel = ctx.kernel();
        NpcSettings settings = new NpcSettings(ctx.config());
        NpcRepository repository = NpcRepositories.cached(persistence);
        NpcPackets packets = new NmsNpcPackets(new PacketSender(new ChannelResolver()));
        NpcViewSpawner spawner = new NpcViewSpawner(packets, kernel.scheduler(), kernel.log(), TAB_HIDE_DELAY);
        NpcRenderer renderer =
                new NpcRenderer(packets, spawner, kernel.scheduler(), RENDER_RANGE, settings.lookRange());
        NpcNotifier notifier = new NpcNotifier(kernel.messages(), kernel.messageSink());
        NpcServices services = assemble(kernel, repository, renderer, notifier);
        spawnStored(repository, renderer);
        MojangSkinService mojangSkins =
                new MojangSkinService(kernel.scheduler(), kernel.log(), new HttpClientFetcher(kernel.log()));
        MineSkinService mineSkins = new MineSkinService(
                kernel.scheduler(),
                kernel.log(),
                new HttpClientFetcher(kernel.log(), MineSkinService.GENERATE_TIMEOUT),
                settings.mineSkinApiKey());
        SkinService skinService = new CompositeSkinService(mojangSkins, mineSkins);
        NpcSkinByName skinByName =
                new NpcSkinByName(skinService, services.skin(), repository, notifier, kernel.scheduler());
        List<CommandRegistration> commands =
                List.of(new NpcCommand(services, renderer::npcNames, skinByName, kernel.messages()));
        BukkitNpcCommandRunner commandRunner = new BukkitNpcCommandRunner();
        BukkitServerConnector connector = new BukkitServerConnector(plugin, kernel.log());
        NpcActionRunner actionRunner = new BukkitNpcActionRunner(
                commandRunner,
                connector,
                kernel.scheduler(),
                kernel.permissions(),
                economy,
                kernel.messages(),
                kernel.log());
        NpcInteractionListener interaction = new NpcInteractionListener(
                renderer, repository, commandRunner, actionRunner, kernel.scheduler(), settings.clickCooldown());
        List<Listener> listeners = List.of(new NpcLifecycleListener(renderer), interaction);
        AutoCloseable refreshTask = kernel.scheduler().repeatGlobal(renderer::refresh, REFRESH_PERIOD, REFRESH_PERIOD);
        Duration lookPeriod = settings.lookPeriod();
        AutoCloseable lookTask = kernel.scheduler().repeatGlobal(renderer::lookTick, lookPeriod, lookPeriod);
        return new Wired(commands, listeners, renderer, connector, refreshTask, lookTask);
    }

    private static NpcServices assemble(
            KernelPorts kernel, NpcRepository repository, NpcRenderer renderer, NpcNotifier notifier) {
        Clock clock = Clock.systemUTC();
        return new NpcServices(
                new CreateNpc(repository, renderer, notifier, kernel.events(), clock),
                new DeleteNpc(repository, renderer, notifier, kernel.events()),
                new ListNpcs(repository, notifier),
                new NearbyNpcs(repository, notifier),
                new MoveNpc(repository, renderer, notifier, kernel.events()),
                new CopyNpc(repository, renderer, notifier, kernel.events(), clock),
                new CenterNpc(repository, renderer, notifier, kernel.events()),
                new FixNpc(repository, renderer, notifier),
                new SetNpcSkin(repository, renderer, notifier),
                new SetNpcEntityType(repository, renderer, notifier),
                new SetNpcClickCommand(repository, notifier),
                new SetNpcLookAtPlayer(repository, renderer, notifier),
                new SetNpcEquipment(repository, renderer, notifier),
                new SetNpcGlowing(repository, renderer, notifier),
                new SetNpcPose(repository, renderer, notifier),
                new SetNpcScale(repository, renderer, notifier),
                new AddNpcAction(repository, notifier),
                new ListNpcActions(repository, notifier),
                new RemoveNpcAction(repository, notifier),
                new ClearNpcActions(repository, notifier),
                new SetNpcTypeData(repository, renderer, notifier),
                new ListNpcTypeData(repository, notifier),
                new MoveNpcTo(repository, renderer, notifier, kernel.events()),
                new SetNpcDisplayName(repository, renderer, notifier),
                new SetNpcInteractionCooldown(repository, notifier),
                new SetNpcMirrorSkin(repository, renderer, notifier),
                new SetNpcCollidable(repository, renderer, notifier),
                new SetNpcShowInTab(repository, renderer, notifier),
                new SetNpcRange(repository, renderer, notifier),
                new SetNpcState(repository, renderer, notifier),
                new SetNpcSkinSlim(repository, renderer, notifier));
    }

    private static void spawnStored(NpcRepository repository, NpcRenderer renderer) {
        // Each render hops onto each viewer's region thread inside the renderer, so this is safe to call straight
        // from the enable path; a viewer out of range or in another world is simply not shown.
        for (Npc npc : repository.all()) {
            renderer.render(npc);
        }
    }

    /**
     * Everything the npc module contributes once wired: the single Brigadier command, the join/quit/world-change
     * listener, the renderer whose fake players must be removed on stop, and the refresh timer handle.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the lifecycle listener to register
     * @param renderer the packet renderer, drained on stop
     * @param connector the proxy connect channel, unregistered on stop so nothing outlives a disable
     * @param refreshTask the global refresh timer handle, cancelled on stop so no task outlives a disable
     * @param lookTask the look-at-player timer handle, cancelled on stop alongside the refresh timer
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            NpcRenderer renderer,
            BukkitServerConnector connector,
            AutoCloseable refreshTask,
            AutoCloseable lookTask) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(connector, "connector");
            Objects.requireNonNull(refreshTask, "refreshTask");
            Objects.requireNonNull(lookTask, "lookTask");
        }

        /** Cancel the timers, unregister the proxy channel, and remove every shown NPC so nothing is orphaned. */
        public void stop() {
            closeQuietly(refreshTask);
            closeQuietly(lookTask);
            connector.close();
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
