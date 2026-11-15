package com.uxplima.uxmessentials.vanish.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vanish.VanishRecordStores;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.vanish.adapter.inbound.command.VanishCommand;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLifecycleListener;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLoginListener;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishProtectionListener;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishSilentContainerListener;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishBuffs;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BusVanishBus;
import com.uxplima.uxmessentials.vanish.adapter.outbound.ForeignVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.PdcVanishPickup;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishActionBar;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishConnectionMessenger;
import com.uxplima.uxmessentials.vanish.application.ApplyVanishOnJoin;
import com.uxplima.uxmessentials.vanish.application.DepartVanished;
import com.uxplima.uxmessentials.vanish.application.JoinVanishReconciler;
import com.uxplima.uxmessentials.vanish.application.ListVanished;
import com.uxplima.uxmessentials.vanish.application.NetworkVanishRoster;
import com.uxplima.uxmessentials.vanish.application.RememberVanish;
import com.uxplima.uxmessentials.vanish.application.SetVanishLevel;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.VanishNetworkSync;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishPickupPreferences;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the vanish context's adapters and use cases over the injected kernel ports, and produces the {@code
 * /vanish} command plus the join/quit listener the plugin registers. This is the one place the vanish context is
 * wired: nothing else news up its classes.
 *
 * <p>Who this backend is hiding is the in-memory {@link InMemoryVanishStore}, the single authority every consumer
 * (messaging, nametags, staff) reads. Each player's vanish is also written to the database through
 * {@link RememberVanish} and read at login by the {@link VanishLoginListener}, so it survives a server switch, and a
 * player who may persist it keeps it through a restart. Its outbound adapters are the store, the level-aware
 * {@link BukkitVanishView}, and the {@link BukkitVanishLevelResolver} that reads a player's see/use levels from their
 * permissions. The store, the toggle, and the resolver are exposed on {@link Wired} so bootstrap can hand them to the
 * consumers' vanish gates (which now read the layered see level) and to staff-mode vanish. On stop the store is cleared
 * so a disable or reload leaves zero residual state.
 *
 * <p>When SuperVanish or PremiumVanish is running, what the consumers get is the {@link ForeignVanishStore} overlay
 * over that same store, so a player the other plugin has hidden is hidden from our tab list and {@code /msg} too. Only
 * the read side is overlaid: our own use cases keep the plain store and never act on somebody else's vanish.
 */
@NullMarked
public final class VanishWiring {

    /** How often the action-bar indicator is re-sent to a vanished player, chosen to outrun the vanilla bar fade. */
    private static final Duration ACTION_BAR_REFRESH = Duration.ofSeconds(1);

    /** A server-name longer than this does not fit the {@code last_server} column, so vanish cannot record it. */
    private static final int MAX_SERVER_NAME = 255;

    /** How often a foreign vanish plugin's hidden players are re-read; five ticks, well under a human's notice. */
    private static final Duration FOREIGN_VANISH_POLL = Duration.ofMillis(250);

    private VanishWiring() {}

    /**
     * Build the vanish adapters and use cases from {@code plugin}, {@code ctx}, the shared {@code persistence}, the
     * cross-server {@code bus}, and the network {@code presence} that says which backend each player is on and which
     * backends are running.
     */
    public static Wired wire(
            Plugin plugin, ModuleContext ctx, Persistence persistence, Bus bus, NetworkPresence presence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(presence, "presence");
        KernelPorts kernel = ctx.kernel();
        VanishConfig config = VanishConfig.from(ctx.config(), kernel.log());

        InMemoryVanishStore store = new InMemoryVanishStore();
        BukkitVanishLevelResolver levels = new BukkitVanishLevelResolver();
        BukkitVanishView view = new BukkitVanishView(plugin, kernel.scheduler(), levels);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        VanishBuffs buffs = new BukkitVanishBuffs(
                plugin.getServer(), kernel.scheduler(), config.nightVision(), config.allowFlight());
        VanishPickupPreferences pickup = new PdcVanishPickup(plugin.getServer(), config.pickupItems());

        if (presence.localServer().length() > MAX_SERVER_NAME) {
            kernel.log()
                    .warn(
                            "network.server-name in config.conf is longer than {} characters, so vanish cannot record"
                                    + " which backend a vanished player is on and a server switch shows them."
                                    + " Shorten it.",
                            MAX_SERVER_NAME);
        }
        RememberVanish remember = new RememberVanish(
                VanishRecordStores.jooq(persistence),
                presence.localServer(),
                kernel.scheduler(),
                Clock.systemUTC(),
                kernel.log());
        CrossServer cross = crossServer(config, bus, presence, store, view, kernel);
        NetworkVanishStore network = cross.network();
        ToggleVanish toggleVanish =
                new ToggleVanish(store, view, levels, notifier, buffs, cross.bus(), kernel.events(), remember);
        SetVanishLevel setVanishLevel = new SetVanishLevel(store, view, levels, buffs, cross.bus(), remember);
        ListVanished listVanished = new ListVanished(store, levels, network);
        JoinVanishReconciler reconciler = new JoinVanishReconciler(network, store);
        ApplyVanishOnJoin applyOnJoin = new ApplyVanishOnJoin(
                reconciler,
                toggleVanish,
                setVanishLevel,
                remember,
                store,
                view,
                levels,
                cross.bus(),
                presence,
                Clock.systemUTC(),
                config.hopWindow());
        DepartVanished departVanished = new DepartVanished(store, cross.bus(), remember);
        VanishLoginListener login =
                new VanishLoginListener(remember, kernel.scheduler(), kernel.log(), config.loadTimeout());
        Function<UUID, Optional<String>> networkName = network::nameOf;
        VanishConnectionMessenger messenger = new VanishConnectionMessenger(kernel.scheduler(), levels, config);
        VanishActionBar actionBar =
                new VanishActionBar(plugin.getServer(), kernel.scheduler(), kernel.messages(), store, config);

        List<CommandRegistration> commands = List.of(new VanishCommand(
                toggleVanish,
                listVanished,
                pickup,
                messenger,
                actionBar,
                plugin.getServer(),
                kernel.messages(),
                networkName));
        List<Listener> listeners = List.of(
                login,
                new VanishLifecycleListener(
                        store,
                        view,
                        applyOnJoin,
                        departVanished,
                        remember,
                        login,
                        kernel.scheduler(),
                        kernel.log(),
                        config,
                        plugin.getServer()),
                new VanishProtectionListener(store, config, pickup),
                new VanishSilentContainerListener(
                        store, kernel.scheduler(), plugin.getServer(), config.silentChests()));
        // A single global timer re-arms the action-bar indicator; a disabled indicator arms no timer at all.
        @Nullable AutoCloseable actionBarTask = config.actionBar()
                ? kernel.scheduler().repeatGlobal(actionBar::refresh, ACTION_BAR_REFRESH, ACTION_BAR_REFRESH)
                : null;
        Foreign foreign = foreign(config, store, plugin.getServer(), kernel);
        // Rows this backend held when it last went down still say the player is here; nobody is, yet.
        remember.releaseOrphans();
        // A third of the hop window keeps a vanished player's sighting fresh across two missed refreshes.
        Duration sighting = config.hopWindow().dividedBy(3);
        AutoCloseable sightingTask =
                kernel.scheduler().repeatGlobal(() -> remember.refresh(store.vanished()), sighting, sighting);
        return new Wired(
                commands,
                listeners,
                store,
                foreign.store(),
                toggleVanish,
                levels,
                network,
                login,
                remember,
                sightingTask,
                actionBarTask,
                foreign.poll());
    }

    /**
     * The authority the consumers read: our own store, or that store with SuperVanish's or PremiumVanish's hidden
     * players folded in when one of them is running and the operator has left the fold on. Only the read side is
     * overlaid. Our own use cases, listeners and the action-bar indicator keep the plain store, so we never announce
     * or act on a vanish that belongs to the other plugin.
     *
     * <p>The other plugin's state is re-read by a repeating global-region task rather than on every question, both
     * because the questions come from render paths and because the online roster is only coherent on that thread
     * under Folia.
     */
    private static Foreign foreign(VanishConfig config, VanishStore store, Server server, KernelPorts kernel) {
        if (!config.readForeignVanish() || !ForeignVanishStore.present(server)) {
            return new Foreign(store, null);
        }
        Logger log = kernel.log();
        ForeignVanishStore.ForeignVanishPoll poll = new ForeignVanishStore.ForeignVanishPoll(server, log);
        log.info("event=foreign_vanish_bound level={}", config.foreignVanishLevel());
        return new Foreign(
                new ForeignVanishStore(store, poll, VanishLevel.of(config.foreignVanishLevel())),
                kernel.scheduler().repeatGlobal(poll::refresh, FOREIGN_VANISH_POLL, FOREIGN_VANISH_POLL));
    }

    /** The read-side authority and, when a foreign vanish plugin is bound, the timer keeping its reading fresh. */
    private record Foreign(VanishStore store, @Nullable AutoCloseable poll) {}

    /**
     * Wire the cross-server vanish sync, or the inert shape when it is off. With {@code cross-server=true} it builds the
     * network-wide view, which drops a peer's entries once the network presence stops counting it as running, the bus bridge
     * that publishes and receives vanish frames, and the sync that applies what the peers send. With it off it returns
     * the disabled bus and empty view, so every cross-server seam is a harmless no-op and same-server vanish is
     * unchanged.
     */
    private static CrossServer crossServer(
            VanishConfig config,
            Bus bus,
            NetworkPresence presence,
            VanishStore store,
            VanishView view,
            KernelPorts kernel) {
        if (!config.crossServer()) {
            return new CrossServer(VanishBus.disabled(), NetworkVanishStore.empty());
        }
        NetworkVanishRoster network = new NetworkVanishRoster(presence, Clock.systemUTC(), config.hopWindow());
        BusVanishBus vanishBus = new BusVanishBus(bus.publisher(), bus.bootNonce(), true);
        vanishBus.subscribe(
                new VanishNetworkSync(network, store, view, vanishBus, kernel.playerLookup(), kernel.scheduler()));
        bus.registry().register(vanishBus::onFrame);
        return new CrossServer(vanishBus, network);
    }

    /** The cross-server seams: the bus a local flip publishes through and the network-wide view queries read. */
    private record CrossServer(VanishBus bus, NetworkVanishStore network) {}

    /**
     * Everything the vanish module contributes once wired: the {@code /vanish} command, the join/quit listener, the
     * in-memory store (the single vanish authority, cleared on stop), the {@link ToggleVanish} use case (so staff-mode
     * vanish routes through it), and the {@link VanishLevelResolver} (so the messaging/nametags gates read the same
     * layered see level).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit listener to register
     * @param store the in-memory vanish authority, cleared on stop
     * @param shared the authority the consumers' vanish gates read: {@code store}, or {@code store} with a foreign
     *     vanish plugin's hidden players folded in
     * @param toggleVanish the vanish use case, exposed for staff-mode vanish
     * @param levels the see/use level resolver, exposed for the messaging/nametags vanish gates
     * @param network the network-wide vanish view, cleared on stop; the empty view when cross-server is off
     * @param login the pre-login read of each player's kept vanish, whose held answers are dropped on stop
     * @param remember the writer of each player's kept vanish, which records a departure for everyone still held on
     *     stop
     * @param sightingTask the repeating refresh of the online vanished players' sightings, cancelled on stop
     * @param actionBarTask the repeating action-bar refresh handle, cancelled on stop; {@code null} when the indicator
     *     is disabled and no timer was armed
     * @param foreignVanishPoll the repeating global-region read of a foreign vanish plugin's hidden players, cancelled
     *     on stop; {@code null} when no such plugin is bound
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            InMemoryVanishStore store,
            VanishStore shared,
            ToggleVanish toggleVanish,
            VanishLevelResolver levels,
            NetworkVanishStore network,
            VanishLoginListener login,
            RememberVanish remember,
            AutoCloseable sightingTask,
            @Nullable AutoCloseable actionBarTask,
            @Nullable AutoCloseable foreignVanishPoll) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(shared, "shared");
            Objects.requireNonNull(toggleVanish, "toggleVanish");
            Objects.requireNonNull(levels, "levels");
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(login, "login");
            Objects.requireNonNull(remember, "remember");
            Objects.requireNonNull(sightingTask, "sightingTask");
        }

        /** Expose the shared {@link VanishStore} authority the consumers' vanish gates read. */
        public VanishStore vanishStore() {
            return shared;
        }

        /**
         * Record a departure for every vanished player still held here, drop the vanish state and cancel the timers, so
         * a disable or reload leaves no residual state. The departures are written on the stopping thread because the
         * server disables plugins before it lets the players go, so no quit reaches this module.
         */
        public void stop() {
            cancel(sightingTask, "sighting");
            remember.releaseAllNow();
            store.clear();
            network.clear();
            login.clear();
            cancel(actionBarTask, "action-bar");
            cancel(foreignVanishPoll, "foreign-vanish poll");
        }

        private static void cancel(@Nullable AutoCloseable task, String what) {
            if (task == null) {
                return;
            }
            try {
                task.close();
            } catch (Exception cancelFailed) {
                // The repeating-task handle's cancel does not throw; close() only declares the checked type.
                throw new IllegalStateException("failed to cancel the vanish " + what + " task", cancelFailed);
            }
        }
    }
}
