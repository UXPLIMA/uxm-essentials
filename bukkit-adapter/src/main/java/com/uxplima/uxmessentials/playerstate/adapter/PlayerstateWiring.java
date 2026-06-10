package com.uxplima.uxmessentials.playerstate.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.PlayerStateCommands;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.NoFlyWorldListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.PlayerStateListener;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.WorldCommandListener;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitInventoryViewer;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitNearbyPlayers;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitPlayerEffects;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitPlayerInfo;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitStateReconciler;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.InMemoryPlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.NoFlyWorldPolicy;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.PlayerStateNotifier;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGlow;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the playerstate context's adapters and use cases over the injected kernel ports, and produces
 * everything the plugin must register: the Brigadier command list and the join/quit/respawn listener. This is
 * the one place the playerstate context is wired — nothing else news up its classes. The context needs no
 * database and no {@code Plugin} handle: its only outbound adapters are the in-memory snapshot store, the
 * reconciler, the effects bridge, and the nearby scan, all of which sit on the kernel {@code Scheduler} port.
 *
 * <p>The {@code heal-remove-effects} toggle (§15.6) is read once from {@code playerstate.conf} here and fixed
 * into the {@link Heal} use case; an operator changes it via a module reload, which re-wires the context.
 */
@NullMarked
public final class PlayerstateWiring {

    private PlayerstateWiring() {}

    /** Build the playerstate adapters and use cases from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(ModuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        ConfigStore config = ctx.config();
        Clock clock = Clock.systemUTC();

        PlayerStateStore store = new InMemoryPlayerStateStore();
        StateReconciler reconciler = new BukkitStateReconciler(kernel.scheduler());
        PlayerEffects effects = new BukkitPlayerEffects(kernel.scheduler());
        InvseeView invseeView = new InvseeView(kernel.messages(), kernel.scheduler());
        InventoryViewer inventoryViewer = new BukkitInventoryViewer(kernel.scheduler(), invseeView);
        NearbyPlayers nearby = new BukkitNearbyPlayers();
        PlayerInfo info = new BukkitPlayerInfo();
        PlayerStateNotifier notifier = new PlayerStateNotifier(kernel.messages(), kernel.messageSink());

        Ports ports = new Ports(store, reconciler, effects, inventoryViewer, nearby, info, notifier);
        PlayerStateServices services = assemble(kernel, config, clock, ports);
        PlayerstateSettings settings = new PlayerstateSettings(config);
        NoFlyWorldPolicy noFlyWorlds = new NoFlyWorldPolicy(settings.noFlyWorlds());
        List<CommandRegistration> commands = PlayerStateCommands.all(services, kernel.messages(), noFlyWorlds);
        List<Listener> listeners = List.of(
                new PlayerStateListener(store, reconciler),
                new InvseeListener(invseeView),
                new WorldCommandListener(settings.worldCommandPolicy(), kernel.messages(), kernel.messageSink()),
                new NoFlyWorldListener(noFlyWorlds, kernel.scheduler(), kernel.messages(), kernel.messageSink()));
        return new Wired(commands, listeners, invseeView);
    }

    private static PlayerStateServices assemble(KernelPorts kernel, ConfigStore config, Clock clock, Ports ports) {
        boolean healRemovesEffects = config.getBoolean("heal-remove-effects", false);
        boolean restEnabled = config.getBoolean("rest-enabled", true);
        var events = kernel.events();
        PlayerStateStore store = ports.store();
        StateReconciler reconciler = ports.reconciler();
        PlayerEffects effects = ports.effects();
        InventoryViewer inventoryViewer = ports.inventoryViewer();
        PlayerStateNotifier notifier = ports.notifier();
        return new PlayerStateServices(
                new ToggleGod(store, reconciler, notifier, events, clock),
                new ToggleFly(store, reconciler, notifier, events, clock),
                new Heal(effects, notifier, events, clock, healRemovesEffects),
                new Feed(effects, notifier, events, clock),
                new SetFoodLevel(effects, notifier),
                new SetHealth(effects, notifier),
                new SetGamemode(store, reconciler, notifier, events, clock),
                new SetSpeed(store, reconciler, notifier, events, clock),
                new Extinguish(effects, notifier),
                new ClearInventory(effects, notifier),
                new OpenContainer(inventoryViewer, notifier),
                new Suicide(effects, notifier),
                new ListNearby(ports.nearby(), notifier),
                new ToggleNightVision(effects, notifier),
                new ToggleGlow(effects, notifier),
                new SetPersonalTime(effects, notifier),
                new SetPersonalWeather(effects, notifier),
                new SetExperience(effects, notifier),
                new SetAir(effects, notifier),
                new Burn(effects, notifier),
                new ShowPosition(ports.info(), notifier),
                new ShowPing(ports.info(), notifier),
                new ShowPlaytime(ports.info(), notifier),
                new ResetRest(effects, notifier, restEnabled),
                kernel.playerLookup());
    }

    /** The context's constructed outbound ports, bundled so {@link #assemble} stays within its argument budget. */
    private record Ports(
            PlayerStateStore store,
            StateReconciler reconciler,
            PlayerEffects effects,
            InventoryViewer inventoryViewer,
            NearbyPlayers nearby,
            PlayerInfo info,
            PlayerStateNotifier notifier) {}

    /**
     * Everything the playerstate module contributes once wired: the Brigadier commands and the listeners (the
     * join/quit/respawn re-apply/reset listener and the {@code /invsee} menu's click/close listener). The
     * context holds no repeating scheduled work; its only durable-while-open state is the set of open invsee
     * menus, which {@link #stop()} reconciles back onto their targets before disable so no edit is lost.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param invseeView the invsee menu, held so {@code stop()} flushes every still-open view
     */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners, InvseeView invseeView) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(invseeView, "invseeView");
        }

        /** Reconcile every still-open invsee menu back onto its target. Called on module stop. */
        public void stop() {
            invseeView.flushAll();
        }
    }
}
