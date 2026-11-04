package com.uxplima.uxmessentials.guard.adapter;

import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand;
import com.uxplima.uxmessentials.guard.adapter.inbound.gui.GuardMenus;
import com.uxplima.uxmessentials.guard.adapter.inbound.listener.ListenerRegistry;
import com.uxplima.uxmessentials.guard.adapter.inbound.listener.LockListener;
import com.uxplima.uxmessentials.guard.adapter.inbound.listener.WandListener;
import com.uxplima.uxmessentials.guard.adapter.movement.BehaviourListener;
import com.uxplima.uxmessentials.guard.adapter.movement.MovementListener;
import com.uxplima.uxmessentials.guard.adapter.movement.PlayerTracks;
import com.uxplima.uxmessentials.guard.adapter.outbound.FileGuardBackups;
import com.uxplima.uxmessentials.guard.adapter.outbound.RegionDisplay;
import com.uxplima.uxmessentials.guard.adapter.outbound.WorldGuardImporter;
import com.uxplima.uxmessentials.guard.application.GuardConfig;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.application.port.GuardBackups;
import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.application.port.GuardSync;
import com.uxplima.uxmessentials.guard.application.port.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.persistence.guard.JooqGuardStore;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.GuardChanged;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;

/**
 * Builds the guard context: store, service, runtime, listeners, movement, command, sync and timers. The one place
 * guard classes are created.
 */
public final class GuardWiring {

    private GuardWiring() {}

    /** What the plugin module keeps: the command to register, the service for the API, and the stop hook. */
    public record Wired(GuardCommand command, GuardService service, GuardRuntime runtime, PlayerTracks tracks,
            Runnable stop) {}

    /** Compiles a world when it loads and forgets it when it unloads. */
    public static final class WorldListener implements Listener {
        private final GuardService service;

        WorldListener(GuardService service) {
            this.service = service;
        }

        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            service.worldLoaded(event.getWorld().getName());
        }

        @EventHandler
        public void onWorldUnload(WorldUnloadEvent event) {
            service.worldUnloaded(event.getWorld().getName());
        }
    }

    /** Wire the guard context. */
    public static Wired wire(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Bus bus,
            ClickActionRunner actions,
            Consumer<String> audit,
            Menus menus,
            MenuBindings menuBindings) {
        KernelPorts kernel = ctx.kernel();
        Logger log = kernel.log();
        Consumer<String> warn = message -> log.warn("event=guard_warning message=\"{}\"", message);
        String serverId = bus.publisher().serverId();
        GuardConfig config = GuardConfig.from(ctx.config(), serverId, warn);
        Clock clock = Clock.systemUTC();
        GuardStore store = new JooqGuardStore(persistence.dsl(), clock, warn);
        GuardBackups backups = new FileGuardBackups(plugin.getDataFolder().toPath().resolve("guard/backups"), clock);
        GuardSync sync = (scope, key) -> bus.publisher().publish(
                new GuardChanged(serverId, scope.value(), key.world(), key.id().value()));
        GuardWorlds worlds = new GuardWorlds() {
            @Override
            public List<String> loaded() {
                return plugin.getServer().getWorlds().stream().map(World::getName).toList();
            }

            @Override
            public Optional<Height> height(String name) {
                World world = plugin.getServer().getWorld(name);
                return world == null ? Optional.empty()
                        : Optional.of(new Height(world.getMinHeight(), world.getMaxHeight() - 1));
            }
        };
        GuardService service = new GuardService(store, sync, worlds, kernel.scheduler(), config, warn);
        GuardRuntime runtime = new GuardRuntime(plugin, kernel.scheduler(), service, store, kernel.messages(),
                kernel.messageSink(), clock, audit);
        Selections selections = new Selections(plugin.getServer());
        RegionDisplay display = new RegionDisplay(plugin);
        PlayerTracks tracks = new PlayerTracks(runtime, actions);
        ListenerRegistry registry = new ListenerRegistry(plugin, runtime);

        List<Listener> fixed = new ArrayList<>();
        fixed.add(new MovementListener(runtime, tracks));
        fixed.add(new BehaviourListener(runtime, tracks));
        fixed.add(new WandListener(runtime, selections));
        if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null
                || plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            fixed.add(new LockListener(runtime, selections));
        }
        fixed.add(new WorldListener(service));
        fixed.forEach(listener -> plugin.getServer().getPluginManager().registerEvents(listener, plugin));

        // Every new snapshot brings the listener families in line and re-applies behaviours to everyone online,
        // each on the thread that owns them.
        service.onSnapshot(snapshot -> kernel.scheduler().onGlobal(() -> {
            registry.sync(snapshot);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                kernel.scheduler().onEntity(BukkitRefs.toRef(player), () -> tracks.refresh(player));
            }
        }));

        bus.registry().register(message -> {
            if (message instanceof GuardChanged changed && !changed.originServer().equals(serverId)) {
                try {
                    service.remoteChanged(new GuardScope(changed.scope()),
                            new RegionKey(changed.world(), RegionId.of(changed.region())));
                } catch (IllegalArgumentException malformed) {
                    warn.accept("ignored a malformed guard change from " + changed.originServer());
                }
            }
        });

        var loaded = service.load().whenComplete((ok, error) -> {
            if (service.loadFailed()) {
                kernel.scheduler().onGlobal(() -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (player.hasPermission(GuardRuntime.NOTIFY_PERMISSION)) {
                            runtime.send(player, GuardMessageKey.LOCKED_WORLD_STAFF, Map.of(), "chat");
                        }
                    }
                });
            }
            if (error == null && !service.loadFailed()) {
                try {
                    backups.write(service.all(), "start", config.backupsKept());
                } catch (RuntimeException e) {
                    warn.accept("guard start backup failed: " + e.getMessage());
                }
            }
        });

        List<AutoCloseable> timers = new ArrayList<>();
        timers.add(kernel.scheduler().repeatGlobal(() -> {
            long now = clock.millis();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                kernel.scheduler().onEntity(BukkitRefs.toRef(player), () -> tracks.tick(player, now));
            }
        }, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        timers.add(kernel.scheduler().repeatGlobal(() -> kernel.scheduler().async(() -> {
            try {
                runtime.flushLog();
                if (service.config().denialLog().enabled()) {
                    store.pruneDenials(clock.instant().minus(Duration.ofDays(service.config().denialLog().keepDays())));
                }
            } catch (RuntimeException e) {
                warn.accept("guard denial log write failed: " + e.getMessage());
            }
        }), Duration.ofSeconds(5), Duration.ofSeconds(5)));
        timers.add(kernel.scheduler().repeatGlobal(service::poll, config.pollInterval(), config.pollInterval()));

        WorldGuardImporter importer = new WorldGuardImporter(plugin, service, backups, warn);
        // The command opens the menus and the menus answer through the command, so the menus are reached through a
        // holder filled right after both exist.
        GuardMenus[] menusHolder = new GuardMenus[1];
        Consumer<Player> openMenu = player -> {
            GuardMenus guardMenus = menusHolder[0];
            if (guardMenus != null) {
                guardMenus.openFor(player);
            }
        };
        BiConsumer<Player, Runnable> onPlayer =
                (player, task) -> kernel.scheduler().onEntity(BukkitRefs.toRef(player), task);
        GuardCommand command = new GuardCommand(
                runtime,
                selections,
                display,
                tracks,
                new CommandFeedback(kernel.messages()),
                importer::run,
                openMenu,
                () -> {
                    ctx.config().reload();
                    var unused = service.reload(GuardConfig.from(ctx.config(), serverId, warn));
                },
                onPlayer,
                config.rgAlias());
        GuardMenus guardMenus = new GuardMenus(menus, service, command, new CommandFeedback(kernel.messages()),
                kernel.messages(), onPlayer);
        guardMenus.register(menuBindings, plugin.getDataFolder().toPath(), log);
        menusHolder[0] = guardMenus;

        Runnable stop = () -> {
            for (AutoCloseable timer : timers) {
                try {
                    timer.close();
                } catch (Exception ignored) {
                    // A timer that fails to cancel stops with the plugin anyway.
                }
            }
            fixed.forEach(HandlerList::unregisterAll);
            registry.close();
            display.clear();
            tracks.restoreAll(plugin.getServer().getOnlinePlayers());
            try {
                runtime.flushLog();
            } catch (RuntimeException e) {
                warn.accept("guard denial log flush on stop failed: " + e.getMessage());
            }
        };
        Objects.requireNonNull(loaded, "loaded");
        return new Wired(command, service, runtime, tracks, stop);
    }
}
