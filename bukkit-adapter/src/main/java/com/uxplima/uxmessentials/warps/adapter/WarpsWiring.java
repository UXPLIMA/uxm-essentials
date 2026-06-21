package com.uxplima.uxmessentials.warps.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.persistence.warps.RedisWarpSync;
import com.uxplima.uxmessentials.persistence.warps.WarpRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WarpSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommands;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpMenuView;
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
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
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
 *
 * <p>Cross-server sync rides the {@link Bus} handle: the wiring registers a {@link WarpSync} listener that
 * drops the cached warp set on a peer's change and wraps the cached repository so every local {@code /warp set}
 * / {@code /warp del} / move announces a {@code WarpChanged} to peers. With the bus disabled the publish is a
 * no-op and the listener is never invoked, so the single-server path is unchanged.
 */
@NullMarked
public final class WarpsWiring {

    private WarpsWiring() {}

    /** Build the warps adapters and use cases with no economy bridge (a recorded warp cost is not charged). */
    public static Wired wire(
            ModuleContext ctx, Persistence persistence, TeleportEngine teleportEngine, Bus bus, GuiLayouts guiLayouts) {
        return wire(ctx, persistence, teleportEngine, Optional.empty(), bus, guiLayouts);
    }

    /**
     * Build the warps context, charging a recorded per-warp cost through {@code economy} when present. The
     * economy context lands before warps in the registry, so its {@link WarpEconomy} bridge is captured during
     * economy wiring and handed in here; when it is empty (economy disabled), a priced warp's cost is recorded
     * but not charged — the soft coupling the warps context owns.
     */
    public static Wired wire(
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Optional<WarpEconomy> economy,
            Bus bus,
            GuiLayouts guiLayouts) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        KernelPorts kernel = ctx.kernel();
        // The cached repository is the read accelerator; the bus listener drops the cached set when a peer
        // reports a change, and the broadcasting decorator announces this backend's own writes to peers.
        CachedWarpRepository cached = WarpRepositories.cachedConcrete(persistence);
        bus.registry().register(WarpSync.listener(cached));
        // Warm the in-memory warp set once on enable so every later /warp resolve, gate, and tab-complete is
        // served from memory — never a synchronous SQLite read on the command thread. This is the only load on
        // the single-server path; the cross-server bus listener drops the set on a peer's change and the next
        // read lazily reloads.
        cached.all();
        WarpRepository repository = WarpSync.repository(cached, bus.publisher());

        // Wire Redis Pub/Sub syncing if enabled
        boolean redisEnabled = ctx.config().getBoolean("redis.enabled", false);
        final @org.jspecify.annotations.Nullable RedisWarpSync redisSync;
        if (redisEnabled) {
            String redisHost = ctx.config().getString("redis.host", "localhost");
            int redisPort = ctx.config().getInt("redis.port", 6379);
            String redisPassword = ctx.config().getString("redis.password", "");
            String redisChannel = ctx.config().getString("redis.channel", "uxmessentials:warps");
            redisSync = new RedisWarpSync(
                    cached, redisHost, redisPort, redisPassword, redisChannel, kernel.scheduler(), kernel.log());
            redisSync.start();
            repository = new RedisBroadcastingRepository(repository, redisSync);
        } else {
            redisSync = null;
        }

        WarpNotifier notifier = new WarpNotifier(kernel.messages(), kernel.messageSink());
        WarpTeleportRegistry teleportRegistry = new WarpTeleportRegistry();
        WarpTeleporter teleporter = new TeleportWarpAdapter(teleportEngine, teleportRegistry);
        GuiLayout menuLayout = guiLayouts.load("warps", "warps-menu", GuiLayout.paginatedDefault(Material.ENDER_PEARL));
        WarpEditorLayout editorLayout =
                guiLayouts.loadWarpEditor("warps", "warps-editor", WarpEditorLayout.defaultLayout());
        var soundLayout = guiLayouts.loadFixedMenu(
                "warps",
                "warps-sound-selector",
                com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView.defaultLayout());
        var particleLayout = guiLayouts.loadFixedMenu(
                "warps",
                "warps-particle-selector",
                com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpParticleSelectorView.defaultLayout());
        var welcomeLayout = guiLayouts.loadFixedMenu(
                "warps",
                "warps-welcome",
                com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpWelcomeMessagesView.defaultLayout());

        var promptListener =
                new com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpChatPromptListener(kernel.messages());
        var playerWarpHandle = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle();
        var editorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView(
                kernel.messages(), kernel.scheduler(), repository, editorLayout, playerWarpHandle);
        var soundSelectorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView(
                kernel.messages(), kernel.scheduler(), soundLayout);
        var particleSelectorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpParticleSelectorView(
                kernel.messages(), kernel.scheduler(), particleLayout);
        var welcomeMessagesView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpWelcomeMessagesView(
                kernel.messages(), kernel.scheduler(), repository, editorView, welcomeLayout);
        var editorListener = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorListener(
                editorView,
                repository,
                promptListener,
                kernel.messages(),
                soundSelectorView,
                particleSelectorView,
                welcomeMessagesView);

        WarpServices services =
                assemble(kernel, repository, notifier, teleporter, economy, menuLayout, editorView, ctx);
        var commands = WarpCommands.all(services, kernel.messages(), () -> ListDisplayMode.from(ctx.config()));
        var listeners = List.<org.bukkit.event.Listener>of(
                new com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpArrivalNotificationListener(
                        kernel.scheduler(), ctx.config(), teleportRegistry),
                new com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpSignListener(
                        repository, services.useWarp(), kernel.permissions(), ctx.config(), kernel.messages()),
                promptListener,
                editorListener);
        return new Wired(
                commands,
                listeners,
                services.listWarps(),
                services.warpMenu(),
                editorView,
                playerWarpHandle,
                teleportRegistry,
                () -> {
                    teleportRegistry.clear();
                    // Drop any pending editor chat prompt so a leftover callback cannot fire after teardown.
                    promptListener.clear();
                    if (redisSync != null) {
                        redisSync.stop();
                    }
                });
    }

    private static final class RedisBroadcastingRepository implements WarpRepository {
        private final WarpRepository delegate;
        private final RedisWarpSync redisSync;

        RedisBroadcastingRepository(WarpRepository delegate, RedisWarpSync redisSync) {
            this.delegate = delegate;
            this.redisSync = redisSync;
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return delegate.find(name);
        }

        @Override
        public List<Warp> all() {
            return delegate.all();
        }

        @Override
        public boolean exists(WarpName name) {
            return delegate.exists(name);
        }

        @Override
        public void save(Warp warp) {
            delegate.save(warp);
            redisSync.publish(warp.name().value());
        }

        @Override
        public void delete(WarpName name) {
            delegate.delete(name);
            redisSync.publish(name.value());
        }

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {
            delegate.rate(name, player, rating);
        }

        @Override
        public double averageRating(WarpName name) {
            return delegate.averageRating(name);
        }
    }

    private static WarpServices assemble(
            KernelPorts kernel,
            WarpRepository repository,
            WarpNotifier notifier,
            WarpTeleporter teleporter,
            Optional<WarpEconomy> economy,
            GuiLayout menuLayout,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView editorView,
            com.uxplima.uxmessentials.shared.application.module.ModuleContext ctx) {
        WarpAccess access = new WarpAccess(kernel.permissions(), economy);
        Clock clock = Clock.systemUTC();
        UseWarp useWarp = new UseWarp(
                repository,
                access,
                teleporter,
                notifier,
                new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                kernel.permissions());
        WarpMenuView warpMenu = new WarpMenuView(kernel.messages(), kernel.scheduler(), useWarp, menuLayout);
        return new WarpServices(
                useWarp,
                new SetWarp(
                        repository,
                        notifier,
                        kernel.events(),
                        clock,
                        ctx.config().getStringList("world-blacklist", List.of())),
                new DelWarp(repository, notifier, kernel.events()),
                new ListWarps(repository, kernel.permissions(), notifier),
                new WarpInfo(repository, notifier),
                new MoveWarp(repository, notifier),
                warpMenu,
                kernel.playerLookup(),
                repository,
                editorView,
                kernel.scheduler());
    }

    /**
     * Everything the warps module contributes once wired: the Brigadier commands and listeners.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the listeners to register
     * @param listWarps the visibility-filtered listing the {@code warps_*}/{@code warp_*} placeholders read
     * @param warpMenu the browse menu the {@code /warp list} command and the management hub both open
     * @param editorView the warp editor view player-warps re-uses for its own editor entry, or {@code null}
     * @param playerWarpHandle the late-bound handle player-warps binds its repository into for the editor
     * @param teleportRegistry the warp-arrival notification handoff player-warps shares so its hops also notify
     * @param stopAction cleanup action on shutdown
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<org.bukkit.event.Listener> listeners,
            ListWarps listWarps,
            WarpMenuView warpMenu,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle playerWarpHandle,
            WarpTeleportRegistry teleportRegistry,
            Runnable stopAction) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(listWarps, "listWarps");
            Objects.requireNonNull(warpMenu, "warpMenu");
            Objects.requireNonNull(playerWarpHandle, "playerWarpHandle");
            Objects.requireNonNull(teleportRegistry, "teleportRegistry");
            Objects.requireNonNull(stopAction, "stopAction");
        }

        public void stop() {
            stopAction.run();
        }
    }
}
