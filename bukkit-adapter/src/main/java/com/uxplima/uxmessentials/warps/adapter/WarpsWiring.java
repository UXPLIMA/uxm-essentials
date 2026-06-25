package com.uxplima.uxmessentials.warps.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.persistence.warps.WarpRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WarpSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommands;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu;
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
 *
 * <p>Cross-server sync rides the {@link Bus} handle: the wiring registers a {@link WarpSync} listener that
 * drops the cached warp set on a peer's change and wraps the cached repository so every local {@code /warp set}
 * / {@code /warp del} / move announces a {@code WarpChanged} to peers. With the bus disabled the publish is a
 * no-op and the listener is never invoked, so the single-server path is unchanged.
 */
@NullMarked
public final class WarpsWiring {

    private WarpsWiring() {}

    /**
     * Build the warps context, charging a recorded per-warp cost through {@code economy} when present. The
     * economy context lands before warps in the registry, so its {@link WarpEconomy} bridge is captured during
     * economy wiring and handed in here; when it is empty (economy disabled), a priced warp's cost is recorded
     * but not charged — the soft coupling the warps context owns.
     *
     * <p>The {@code menus}/{@code menuBindings} pair is the data-driven menu engine: warps registers the sound
     * selector's list source, placeholders, and actions through the bindings and opens it through the façade. This
     * is the engine pilot and the pattern the remaining feature menus follow when they migrate.
     */
    public static Wired wire(
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Optional<WarpEconomy> economy,
            Bus bus,
            GuiLayouts guiLayouts,
            TextInput textInput,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus menus,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings menuBindings) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        // The cached repository is the read accelerator; the bus listener drops the cached set when a peer
        // reports a change, and the broadcasting decorator announces this backend's own writes to peers.
        CachedWarpRepository cached = WarpRepositories.cachedConcrete(
                persistence,
                com.uxplima.uxmessentials.shared.adapter.outbound.lookup.PlayerNames.resolver(kernel.playerLookup()));
        var categoryRepository = WarpRepositories.categoriesConcrete(persistence);
        bus.registry().register(WarpSync.listener(cached));
        // A peer's warp change can also reassign categories, so the same WarpChanged signal that drops the cached
        // warp set also drops the cached category set; with the bus disabled this listener is never invoked.
        bus.registry().register(message -> {
            if (message instanceof com.uxplima.uxmessentials.shared.network.WarpChanged) {
                categoryRepository.invalidateAll();
            }
        });
        // Warm the in-memory warp set once on enable so every later /warp resolve, gate, and tab-complete is
        // served from memory — never a synchronous SQLite read on the command thread. This is the only load on
        // the single-server path; the cross-server bus listener drops the set on a peer's change and the next
        // read lazily reloads.
        cached.all();
        WarpRepository repository = WarpSync.repository(cached, bus.publisher());

        WarpNotifier notifier = new WarpNotifier(kernel.messages(), kernel.messageSink());
        WarpTeleportRegistry teleportRegistry = new WarpTeleportRegistry();
        WarpTeleporter teleporter = new TeleportWarpAdapter(teleportEngine, teleportRegistry);
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

        var playerWarpHandle = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle();
        var playerWarpGoTo = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle();
        var editorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView(
                kernel.messages(), kernel.scheduler(), repository, editorLayout, playerWarpHandle);
        var soundSelectorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundSelectorView(
                kernel.messages(), kernel.scheduler(), soundLayout);
        var soundMenu = com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpSoundMenu.create(
                menus, soundSelectorView, repository, editorView, textInput);
        soundMenu.register(menuBindings, guiLayouts.dataFolder(), kernel.log());
        var particleSelectorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpParticleSelectorView(
                kernel.messages(), kernel.scheduler(), particleLayout);
        var welcomeMessagesView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpWelcomeMessagesView(
                kernel.messages(), kernel.scheduler(), repository, editorView, welcomeLayout);
        // UseWarp is built once here so the editor's "go to" button and the /warp command share the exact same
        // teleport path, then handed to assemble() rather than rebuilt there.
        UseWarp useWarp = new UseWarp(
                repository,
                new WarpAccess(kernel.permissions(), economy),
                teleporter,
                notifier,
                new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                kernel.permissions());
        // Warm the category set once on enable so every browse-menu open and category-GUI render is served from
        // memory rather than a synchronous SQLite read on the command/region thread.
        categoryRepository.all();
        var categoryManagerView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryManagerView(
                kernel.messages(), categoryRepository, kernel.scheduler());
        var categorySettingsView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView(
                kernel.messages(), kernel.scheduler());
        var categorySelectorView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySelectorView(
                kernel.messages(), categoryRepository, kernel.scheduler());
        var categoryParentSelectorView =
                new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryParentSelectorView(
                        kernel.messages(), categoryRepository, kernel.scheduler());
        SetWarp setWarp = new SetWarp(
                repository,
                notifier,
                kernel.events(),
                Clock.systemUTC(),
                ctx.config().getStringList("world-blacklist", List.of()));
        // The admin warp manager renders through the always-on menu engine. The create flow that opens a fresh warp's
        // editor is pulled out so the engine-rendered manager can drive it. A one-element holder breaks the cycle: the
        // manager's create button reopens the manager on cancel, and the category manager's back button reopens it,
        // but both are wired before the manager exists.
        var createPrompt = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCreatePrompt(
                kernel.messages(), textInput, setWarp, editorView);
        var managerHolder = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerMenu[1];
        var managerView = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerMenu(
                menus,
                kernel.scheduler(),
                repository,
                kernel.messages(),
                editorView,
                categoryManagerView,
                createPrompt.boundTo((player, viewer) -> managerHolder[0].open(player, viewer)));
        managerHolder[0] = managerView;
        managerView.register(menuBindings, guiLayouts.dataFolder(), kernel.log());
        // The read-only /warp browse menu now renders through the always-on menu engine. It opens off the same UseWarp
        // path the command drives and resolves its tiles off the warm warp/category sets, so the engine renders without
        // a port read of its own.
        var browseMenu = new WarpBrowseMenu(menus, kernel.scheduler(), useWarp, kernel.messages(), categoryRepository);
        browseMenu.register(menuBindings, guiLayouts.dataFolder(), kernel.log());
        var categoryEditing = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategoryEditing(
                (player, viewer) -> managerView.open(player, viewer),
                categoryManagerView,
                categorySettingsView,
                categoryParentSelectorView,
                categoryRepository,
                repository,
                editorView,
                textInput,
                kernel.messages());
        var editorListener = new com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorListener(
                editorView,
                repository,
                textInput,
                kernel.messages(),
                soundSelectorView,
                particleSelectorView,
                welcomeMessagesView,
                useWarp,
                playerWarpGoTo,
                categorySelectorView,
                categoryEditing,
                soundMenu);

        WarpServices services =
                assemble(kernel, repository, notifier, browseMenu, editorView, ctx, useWarp, managerView);
        var commands = WarpCommands.all(services, kernel.messages(), () -> ListDisplayMode.from(ctx.config()));
        var listeners = List.<org.bukkit.event.Listener>of(
                new com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpArrivalNotificationListener(
                        kernel.scheduler(), ctx.config(), teleportRegistry),
                new com.uxplima.uxmessentials.warps.adapter.inbound.listener.WarpSignListener(
                        repository, services.useWarp(), kernel.permissions(), ctx.config(), kernel.messages()),
                editorListener);
        return new Wired(
                commands,
                listeners,
                services.listWarps(),
                services.warpMenu(),
                editorView,
                playerWarpHandle,
                playerWarpGoTo,
                teleportRegistry,
                teleportRegistry::clear);
    }

    private static WarpServices assemble(
            KernelPorts kernel,
            WarpRepository repository,
            WarpNotifier notifier,
            WarpBrowseMenu browseMenu,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView editorView,
            com.uxplima.uxmessentials.shared.application.module.ModuleContext ctx,
            UseWarp useWarp,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerMenu managerView) {
        Clock clock = Clock.systemUTC();
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
                browseMenu,
                kernel.playerLookup(),
                repository,
                editorView,
                managerView,
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
     * @param playerWarpGoTo the late-bound handle player-warps binds its go-to teleport into for the editor button
     * @param teleportRegistry the warp-arrival notification handoff player-warps shares so its hops also notify
     * @param stopAction cleanup action on shutdown
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<org.bukkit.event.Listener> listeners,
            ListWarps listWarps,
            WarpBrowseMenu warpMenu,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle playerWarpHandle,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpGoToHandle playerWarpGoTo,
            WarpTeleportRegistry teleportRegistry,
            Runnable stopAction) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(listWarps, "listWarps");
            Objects.requireNonNull(warpMenu, "warpMenu");
            Objects.requireNonNull(playerWarpHandle, "playerWarpHandle");
            Objects.requireNonNull(playerWarpGoTo, "playerWarpGoTo");
            Objects.requireNonNull(teleportRegistry, "teleportRegistry");
            Objects.requireNonNull(stopAction, "stopAction");
        }

        public void stop() {
            stopAction.run();
        }
    }
}
