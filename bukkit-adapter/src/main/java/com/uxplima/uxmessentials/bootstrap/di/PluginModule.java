package com.uxplima.uxmessentials.bootstrap.di;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.bootstrap.CommandAliasDefaults;
import com.uxplima.uxmessentials.bootstrap.command.BackupCommand;
import com.uxplima.uxmessentials.bootstrap.command.GuiSubcommand;
import com.uxplima.uxmessentials.bootstrap.command.HelpCommand;
import com.uxplima.uxmessentials.bootstrap.command.LangCommand;
import com.uxplima.uxmessentials.bootstrap.command.MigrationImportNode;
import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.bootstrap.health.BusTransportHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.ClusterPeersHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.DatabaseHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.EconomyProviderHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.ModuleCountHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SchedulerHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SoftDependencyHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.UpdateHealthCheck;
import com.uxplima.uxmessentials.communication.adapter.CommunicationWiring;
import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordlinkWiring;
import com.uxplima.uxmessentials.economy.adapter.EconomyWiring;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.holograms.adapter.HologramsWiring;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders;
import com.uxplima.uxmessentials.homes.adapter.HomesWiring;
import com.uxplima.uxmessentials.homes.adapter.outbound.RepositoryHomeRespawnLocator;
import com.uxplima.uxmessentials.homes.application.HomeRespawnLocator;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldWiring;
import com.uxplima.uxmessentials.kits.adapter.KitsWiring;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.messaging.adapter.MessagingWiring;
import com.uxplima.uxmessentials.messaging.adapter.MutableAfkStatus;
import com.uxplima.uxmessentials.messaging.adapter.MutableMutePolicy;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PresenceAfkStatus;
import com.uxplima.uxmessentials.migration.MigrationModule;
import com.uxplima.uxmessentials.migration.adapter.DataDirBackupSnapshot;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.migration.adapter.MigrationWiring;
import com.uxplima.uxmessentials.moderation.adapter.ModerationWiring;
import com.uxplima.uxmessentials.nametags.adapter.NametagsWiring;
import com.uxplima.uxmessentials.npc.adapter.NpcWiring;
import com.uxplima.uxmessentials.persistence.communication.AnnouncementStores;
import com.uxplima.uxmessentials.persistence.playerstate.PlaytimeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerstateWiring;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerwarpsWiring;
import com.uxplima.uxmessentials.presence.adapter.PresenceWiring;
import com.uxplima.uxmessentials.scoreboard.adapter.ScoreboardWiring;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusWiring;
import com.uxplima.uxmessentials.shared.adapter.outbound.config.CommandCatalogConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.nametag.NameVisibilityCoordinator;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.BukkitServerMetrics;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.GateModerationPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.KitAccessPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ProviderEconomyPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryHologramsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryHomesPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryPlayerwarpsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryVaultsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryVotePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryWarpsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ServicesTeleportPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StaffStaffPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreCommunicationPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreDiscordlinkPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePlayerstatePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePresencePlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StoreScoreboardPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateCheckSettings;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalog;
import com.uxplima.uxmessentials.shared.application.command.CommandCatalogRenderer;
import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.staff.adapter.StaffWiring;
import com.uxplima.uxmessentials.tablist.adapter.TablistWiring;
import com.uxplima.uxmessentials.teleport.adapter.MutableHomeRespawnLocator;
import com.uxplima.uxmessentials.teleport.adapter.MutableJailGate;
import com.uxplima.uxmessentials.teleport.adapter.TeleportWiring;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.vaults.adapter.VaultsWiring;
import com.uxplima.uxmessentials.vote.adapter.VoteWiring;
import com.uxplima.uxmessentials.warps.adapter.WarpsWiring;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.worlds.adapter.WorldsWiring;
import com.uxplima.uxmessentials.worlds.adapter.outbound.LinkedWorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.NullMarked;

/**
 * The single hand-rolled DI site. Consults the {@link ModuleRegistry} so a disabled module wires
 * nothing — no adapters, no commands, no listeners, no migrations, no runtime state.
 *
 * <p>The wiring invariant: the only path to constructing a context's adapters is through its enabled
 * {@code FeatureModule}. Nothing here news up a context's classes directly; the loop starts each
 * enabled module and the module owns its own construction inside {@code start}. The {@code JavaPlugin}
 * is held only here, in bootstrap — it never leaks into application or adapter code.
 */
@NullMarked
public final class PluginModule {

    private PluginModule() {}

    /** Wires the plugin and returns the resources to close on disable. */
    public static CloseableResources wire(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        com.uxplima.uxmessentials.shared.application.port.Logger kernelLog = KernelWiring.logger(plugin);
        ConfigStore config = KernelWiring.loadConfig(plugin, kernelLog);
        KernelWiring.Kernel wiredKernel = KernelWiring.wire(plugin, config, kernelLog);
        KernelPorts kernel = wiredKernel.ports();
        ModuleRegistry registry = new DefaultModuleRegistry();
        // The hand-wired hub registry every module's management GUI plugs into. Built once here and handed to
        // the hub command below; module wiring (SP1+) registers each module's opener into it. Empty until then.
        ManagementGuiRegistry guiRegistry = new ManagementGuiRegistry();
        CloseableResources resources = new CloseableResources();
        // Every published command is wrapped so the requesting player's locale binds at the boundary.
        resources.localeBinding(new LocaleBinding(wiredKernel.localeStore(), wiredKernel.serverDefault()));
        // A bare arg-only command answers with its usage instead of Brigadier's red parse error; injected
        // between the catalog and the locale wrap so the usage line resolves in the player's language.
        resources.usageBinding(new UsageBinding(kernel.messages()));

        Persistence persistence = KernelWiring.openPersistence(plugin, config, kernelLog, registry);
        // The pool is closed last (pushed first), after every module has stopped and drained its writes.
        resources.onClose(persistence::close);

        // The cross-server bus is a kernel concern (one per backend), built before the modules so each context
        // that opts into sync registers its listener and wraps its repository through the Bus handle. The
        // channel is registered only after every listener is in place (start, below); a disabled backend gets a
        // no-op bus and registers no channel, so the single-server path is unchanged.
        BusWiring.Wired bus = BusWiring.wire(plugin, config, kernel.scheduler(), kernel.log());
        resources.onClose(bus::stop);

        PlaceholderContexts placeholders =
                wireModules(plugin, registry, config, kernel, persistence, resources, log, bus.bus(), guiRegistry);
        bus.start();
        registerPlaceholders(plugin, placeholders, resources, kernel.log());
        // Cross-cutting server-integration polish (1.21+ pause-menu links + opt-in update checker + map-marker
        // integration). These belong to no feature context: server links apply once on enable, the update checker —
        // off by default, built on the uxmLib update toolkit — self-registers its permission-gated join notice and
        // returns a stop hook for its recurring check, and the map-marker integration renders warps/spawns onto
        // Dynmap/squaremap when one is installed (homes opt-in).
        IntegrationsWiring.Wired integrations = IntegrationsWiring.wire(plugin, config, kernel, persistence);
        resources.onClose(integrations.stop());
        MigrationImportNode importNode = wireMigration(plugin, config, kernel, persistence);
        List<HealthCheck> healthChecks = healthChecks(plugin, registry, config, persistence, bus);
        // The management-GUI hub is bootstrap-level (no feature context owns it): /uxmess gui draws the
        // ManagementGuiRegistry entries the viewer is permitted, each opening that module's own GUI. The
        // registry is constructed here and is threaded to module wiring (SP1+ each registers its opener);
        // an empty registry is the valid first state, so /uxmess gui replies with the empty-hub line until a
        // module plugs in. Geometry/materials load disk-first then bundled from modules/management/gui/hub.conf.
        GuiText guiText = new GuiText(kernel.messages());
        GuiLayouts guiLayouts = new GuiLayouts(plugin.getDataFolder().toPath(), kernel.log());
        EntityListLayout hubLayout =
                guiLayouts.loadEntityList("management", "hub", EntityListLayout.paginatedDefault(Material.NETHER_STAR));
        ManagementHubView hub =
                new ManagementHubView(guiText, kernel.scheduler(), kernel.permissions(), guiRegistry, hubLayout);
        GuiSubcommand guiNode = new GuiSubcommand(guiRegistry, hub, kernel.permissions(), kernel.messages());
        resources.addCommand(
                new UxmessCommand(registry, config, importNode, guiNode, kernel.scheduler(), healthChecks));
        // /lang is cross-cutting (not a feature context), so it is wired here in the bootstrap surface.
        resources.addCommand(new LangCommand(
                wiredKernel.localeStore(), wiredKernel.catalog(), kernel.messages(), kernel.messageSink()));
        // /backup reuses the migration importer's data-dir snapshot (a "backup-" prefixed copy of the
        // bundled .conf state), run off-tick through the Scheduler. Operator-level, so it too lives here.
        resources.addCommand(new BackupCommand(
                new DataDirBackupSnapshot(plugin.getDataFolder().toPath(), "backup", kernel.log()),
                kernel.scheduler(),
                kernel.log()));
        // /help is cross-cutting too. It reads the same resolved, module-filtered registration set the plugin
        // publishes — supplied lazily so it reflects every catalog rename and disable — and filters it per the
        // sender's own permission. The supplier is evaluated on each invocation, well after wiring completes.
        resources.addCommand(new HelpCommand(resources::commands, kernel.messages()));
        // Resolved last, once every module and the bootstrap commands have contributed, so the catalog sees
        // the full default surface before it applies the operator's rename/alias/disable choices.
        applyCatalog(plugin, kernel, resources);
        return resources;
    }

    private static void applyCatalog(JavaPlugin plugin, KernelPorts kernel, CloseableResources resources) {
        List<CommandDefinition> defs = CommandAliasDefaults.augment(resources.registered().stream()
                .map(r -> new CommandDefinition(new CommandId(r.commandId()), r.defaultName(), r.defaultAliases()))
                .toList());
        CommandCatalogConfig.Loaded loaded =
                new CommandCatalogConfig(plugin.getDataFolder().toPath(), kernel.log()).loadAll();
        CommandCatalog.Resolution resolution = CommandCatalog.resolve(defs, loaded.overrides(), loaded.guiDefault());
        resolution.warnings().forEach(w -> kernel.log().warn("command catalog: {}", w.message()));
        // Seed the editable file from the resolved surface so a fresh install lands a self-documenting
        // commands.conf instead of an empty folder the catalog would silently ignore.
        writeDefaultCatalog(plugin, resolution.effective(), kernel.log());
        Map<String, EffectiveCommand> byId =
                resolution.effective().stream().collect(Collectors.toMap(e -> e.id().value(), e -> e));
        resources.catalogBinding(new CatalogBinding(byId));
        // The GUI binding reuses the same resolved map so a command's gui flag and rename agree on one entry.
        resources.guiRootBinding(new GuiRootBinding(byId));
    }

    private static void writeDefaultCatalog(
            JavaPlugin plugin,
            List<EffectiveCommand> effective,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        // Skip if any commands/ directory already exists: an operator who created or split the file keeps
        // full control, and renames/alias edits survive every restart and upgrade untouched.
        Path dir = plugin.getDataFolder().toPath().resolve("commands");
        if (Files.exists(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("commands.conf"), CommandCatalogRenderer.render(effective));
            log.info("wrote default command catalog with {} commands", effective.size());
        } catch (IOException failure) {
            log.error("failed to write default command catalog to " + dir, failure);
        }
    }

    private static void registerPlaceholders(
            JavaPlugin plugin,
            PlaceholderContexts placeholders,
            CloseableResources resources,
            com.uxplima.uxmessentials.shared.application.port.Logger log) {
        // The PlaceholderAPI expansion is registered only when the plugin is present and at least one context
        // contributed a read seam; otherwise this is a no-op and nothing is closed on disable. The registration
        // happens after every context is wired so every enabled placeholder group is reachable.
        PlaceholderApiSupport.registerExpansion(
                        placeholders, plugin.getPluginMeta().getVersion(), log)
                .ifPresent(registration -> resources.onClose(registration::close));
    }

    private static MigrationImportNode wireMigration(
            JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        // The migration adapter is command-gated, not a steady-state feature context, so it is wired here in
        // the operator surface rather than registered in the feature-module registry. Its enable gate ships
        // disabled; an enabled module publishes a live /uxmess import, a disabled one a dormant command that
        // reports the importer off. Nothing runs at enable — the importer fires only on the command.
        MigrationModule module = new MigrationModule();
        MigrationImportService service = MigrationWiring.wire(
                plugin,
                persistence,
                config.scoped(module.id().configRoot()),
                config.scoped(ModuleId.of("economy").configRoot()),
                kernel.scheduler(),
                kernel.log(),
                module.enabled(config));
        return new MigrationImportNode(service);
    }

    private static List<HealthCheck> healthChecks(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            Persistence persistence,
            BusWiring.Wired bus) {
        // Assembled after the modules are wired so the set reflects what is actually present: the database and
        // soft-depend probes always apply, the economy-provider ownership check only when economy is enabled.
        // The /uxmess doctor command runs each one off-tick (each is wrapped in HealthCheck.safe so a probe that
        // throws becomes a FAIL line rather than aborting the run).
        List<HealthCheck> checks = new ArrayList<>();
        checks.add(new DatabaseHealthCheck(persistence));
        if (economyEnabled(registry, config)) {
            checks.add(new EconomyProviderHealthCheck(plugin.getServer().getServicesManager(), plugin));
        }
        // The Redis probe reads the unified network.redis block from the plugin-wide config, not a per-module one.
        checks.add(new SoftDependencyHealthCheck(plugin.getServer().getPluginManager(), config));
        // The bus line reads the running transport's live healthy() flag (a cheap volatile read) so the operator
        // sees whether cross-server delivery is actually working, not just configured.
        checks.add(new BusTransportHealthCheck(bus.health()));
        // The cluster-peer line reads the in-memory roster the presence heartbeat feeds; only an enabled backend
        // has a roster, so the check is added only when the bus is enabled.
        bus.peers().ifPresent(peers -> checks.add(new ClusterPeersHealthCheck(peers)));
        checks.add(new SchedulerHealthCheck());
        checks.add(new UpdateHealthCheck(UpdateCheckSettings.from(config).enabled()));
        checks.add(new ModuleCountHealthCheck(registry, config));
        return List.copyOf(checks);
    }

    private static boolean economyEnabled(ModuleRegistry registry, ConfigStore config) {
        return registry.byId(ModuleId.of("economy"))
                .map(module -> module.enabled(config))
                .orElse(false);
    }

    private static PlaceholderContexts wireModules(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            KernelPorts kernel,
            Persistence persistence,
            CloseableResources resources,
            Logger log,
            Bus bus,
            ManagementGuiRegistry guiRegistry) {
        // teleport is wired before homes/warps (registry order is dependency-first), so its engine is
        // captured and handed to the contexts that delegate teleport execution to it.
        ContextLinks links = new ContextLinks();
        // Install uxmLib's single menu listener once, before any GUI-using module (vaults, itemworld) wires,
        // and tear it down on disable so a reload re-installs cleanly (the static install state is reset).
        Guis.install(plugin);
        resources.onClose(Guis::uninstall);
        // One anvil-input listener shared by every GUI-using context's create/rename prompts. Installed once here
        // (before any module wires) and torn down on disable; sessions are keyed per player and a player has at most
        // one anvil open, so a single installed instance is safe and avoids per-module listeners that never fire when
        // a module forgets to install its own.
        com.uxplima.uxmlib.gui.anvil.AnvilInput anvil = new com.uxplima.uxmlib.gui.anvil.AnvilInput(plugin);
        anvil.install();
        resources.onClose(anvil::uninstall);
        // The browse-menu layout loader resolves modules/<m>/gui/<name>.conf disk-first then bundled; built once
        // here with the data folder so every GUI-using context loads its layout the same way.
        GuiLayouts guiLayouts = new GuiLayouts(plugin.getDataFolder().toPath(), kernel.log());
        for (FeatureModule module : registry.enabledModules(config)) {
            ConfigStore moduleConfig = config.scoped(module.id().configRoot());
            ModuleContext ctx = new ModuleContext(module.id(), moduleConfig, kernel);
            if (skippedByCapability(module, ctx, log)) {
                continue;
            }
            startModule(module, ctx, resources, log);
            wireAdapters(plugin, module, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        }
        // The server-metrics seam belongs to no feature context — it reads Bukkit/JVM globals — so it is wired
        // unconditionally here, after the modules, with the plugin-enable timestamp so its uptime is measured
        // from this enable (a reload restarts it) rather than the whole JVM's age.
        links.placeholders.serverMetrics(new BukkitServerMetrics(plugin.getServer(), Instant.now()));
        return links.placeholders.build();
    }

    private static void wireAdapters(
            JavaPlugin plugin,
            FeatureModule module,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // The bukkit-side adapters of each context are wired here once the context's pure module has
        // started. teleport builds its durable jOOQ spawn directory over persistence.dsl(); homes builds
        // its jOOQ repository the same way and delegates execution to the captured teleport engine.
        if (module.id().equals(ModuleId.of("teleport"))) {
            wireTeleport(plugin, ctx, persistence, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("worlds"))) {
            wireWorlds(plugin, ctx, persistence, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("homes"))) {
            wireHomes(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("economy"))) {
            wireEconomy(plugin, ctx, persistence, resources, links, bus, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("warps"))) {
            wireWarps(ctx, persistence, resources, links, bus, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("kits"))) {
            wireKits(plugin, ctx, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("playerstate"))) {
            wirePlayerstate(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("messaging"))) {
            wireMessaging(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("presence"))) {
            wirePresence(plugin, ctx, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("moderation"))) {
            wireModeration(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("itemworld"))) {
            wireItemworld(plugin, ctx, resources, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("vaults"))) {
            wireVaults(plugin, ctx, persistence, resources, bus, links, guiRegistry);
        } else if (module.id().equals(ModuleId.of("communication"))) {
            wireCommunication(plugin, ctx, persistence, resources, links, guiLayouts, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("holograms"))) {
            wireHolograms(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("playerwarps"))) {
            wirePlayerwarps(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        } else if (module.id().equals(ModuleId.of("scoreboard"))) {
            wireScoreboard(plugin, ctx, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("tablist"))) {
            wireTablist(plugin, ctx, resources);
        } else if (module.id().equals(ModuleId.of("vote"))) {
            wireVote(plugin, ctx, persistence, resources, links, bus);
        } else if (module.id().equals(ModuleId.of("discordlink"))) {
            wireDiscordlink(plugin, ctx, persistence, resources, links, guiLayouts, guiRegistry);
        } else if (module.id().equals(ModuleId.of("nametags"))) {
            wireNametags(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("staff"))) {
            wireStaff(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("npc"))) {
            wireNpc(plugin, ctx, persistence, resources, links, bus, guiLayouts, guiRegistry, anvil);
        }
    }

    private static void wireTeleport(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        TeleportWiring.Wired wired = TeleportWiring.wire(plugin, ctx, persistence, guiLayouts, guiRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.teleportEngine = wired.services().engine();
        // The teleport PAPI seam reads the same cooldown gate, warmup tracker, /back store, tpa registry and
        // /tptoggle flags the teleport commands do, so a placeholder matches what the player experiences.
        links.placeholders.teleport(new ServicesTeleportPlaceholders(
                ctx.kernel().cooldowns(),
                wired.services().warmupTracker(),
                wired.services().backStore(),
                wired.services().requests(),
                wired.services().flags(),
                java.time.Clock.systemUTC()));
        // Captured for staff (wired last), which binds its COMPASS gadget and /stafflist to this admin engine.
        links.staffTeleport = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.TeleportSeam(
                wired.services().engine());
        // Captured for moderation, which lands later and rebinds this jail gate to the real jail policy.
        links.jailGate = wired.jailGate();
        // Captured for homes, which lands later and rebinds this seam so the respawn chain's HOME step resolves.
        links.homeRespawnLocator = wired.homeRespawnLocator();
    }

    private static void wireWorlds(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // worlds builds its cached jOOQ WorldRepository over persistence.dsl() and its BukkitWorldEngine over the
        // plugin's Server. It delegates /worlds tp and /worlds spawn execution to the captured teleport engine (wired
        // earlier) and charges the per-world entry fee through the economy bridge — but economy lands after worlds, so
        // the fee resolves its provider/currency lazily at charge time (free until economy is up, free for good when
        // economy is disabled). The enable-time reconcile (adopt already-loaded worlds, auto-load registered ones) is
        // kicked on the global region thread the moment wiring completes, then drops the warm snapshot and refreshes
        // the
        // import-folder candidates off-tick. The in-process bus is the concrete publisher so the live-apply subscriber
        // (re-apply stored settings on world load / setting change) can be registered here and unsubscribed on stop;
        // the
        // kernel port exposes only publish.
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine,
                "worlds /worlds tp and /worlds spawn delegate teleport execution but the teleport engine is unavailable");
        WorldEntryFee entryFee = new LinkedWorldEntryFee(() -> links.economyProvider, () -> links.economyCurrency);
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        WorldsWiring.Wired wired = WorldsWiring.wire(
                ctx,
                persistence,
                plugin.getServer(),
                events,
                engine,
                entryFee,
                guiLayouts,
                plugin.getDataFolder().toPath());
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Capture the generator resolver for the plugin's getDefaultWorldGenerator hook before kicking the
        // reconcile: auto-load may load a world declaring generator: uxmEssentials:void|flat, which routes
        // back through that hook, so the resolver must be reachable first.
        resources.worldGeneratorResolver(wired.generatorResolver());
        links.placeholders.worlds(wired.worldsPlaceholders());
        wired.startReconcile().run();
        resources.onClose(wired.stop());
        // Open the same /world gui world picker from the management hub, gated by the existing world-gui node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "worlds",
                com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey.LIST_TITLE,
                Material.GRASS_BLOCK,
                "uxmessentials.world.gui",
                (player, viewer) -> wired.listView().open(player, viewer, 0)));
    }

    private static void wireHomes(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "homes delegates teleport execution but the teleport engine is unavailable");
        HomesWiring.Wired wired = HomesWiring.wire(
                plugin, ctx, persistence, engine, Optional.ofNullable(links.homeEconomy), bus, guiLayouts, resources);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Rebind the teleport context's home-respawn seam (built while it still resolved to empty) to the
        // cache-backed locator, so a HOME step in a configured respawn chain now lands on the player's home.
        // When teleport is disabled its holder is absent and this bind is a no-op.
        bindHomeRespawn(links, new RepositoryHomeRespawnLocator(wired.repository()));
        links.placeholders.homes(new RepositoryHomesPlaceholders(wired.repository(), wired.quota()));
        // Open the same /home slot-grid menu from the management hub, gated by the existing home-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "homes",
                com.uxplima.uxmessentials.homes.application.HomesMessageKey.HOME_MENU_TITLE,
                Material.RED_BED,
                "uxmessentials.home.use",
                (player, viewer) -> wired.listView().open(player, viewer)));
    }

    private static void bindHomeRespawn(ContextLinks links, HomeRespawnLocator locator) {
        MutableHomeRespawnLocator holder = links.homeRespawnLocator;
        if (holder != null) {
            holder.bind(locator);
        }
    }

    private static void wireEconomy(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        EconomyWiring.Wired wired = EconomyWiring.wire(plugin, ctx, persistence, bus, anvil);
        links.economyProvider = wired.provider();
        links.economyCurrency = wired.defaultCurrency();
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.start();
        resources.onClose(wired::stop);
        // Open the same /wallet dashboard from the management hub, gated by the existing wallet node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "economy",
                com.uxplima.uxmessentials.economy.application.EconomyMessageKey.WALLET_GUI_TITLE,
                Material.GOLD_INGOT,
                "uxmessentials.economy.wallet",
                (player, viewer) -> wired.walletView().open(player, wired.defaultCurrency())));
        // Captured for warps, kits, homes, and vaults, which land after economy and charge a recorded cost
        // through it.
        links.warpEconomy = wired.warpEconomy();
        links.kitEconomy = wired.kitEconomy();
        links.homeEconomy = wired.homeEconomy();
        links.vaultEconomy = wired.vaultEconomy();
        links.npcEconomy = wired.npcEconomy();
        links.placeholders.economy(new ProviderEconomyPlaceholders(
                wired.provider(), wired.defaultCurrency(), wired.amountFormat(), BalTop.MAX_PAGE_SIZE));
        // Publish a balance leaderboard source for the holograms module (wired later): the lock-free baltop
        // snapshot, mapped into ranked name/score rows. The composition root is the only place that may bridge
        // the two contexts, so the provider is a lambda here rather than a class in either context.
        BaltopSnapshots baltop = wired.snapshots();
        Currency currency = wired.defaultCurrency();
        links.balanceLeaderboard = limit -> baltop.top(currency, limit).stream()
                .map(row -> new LeaderboardEntry(row.owner().name(), MoneyFormat.withSymbol(row.balance())))
                .toList();
    }

    private static void wireWarps(
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "warps delegates teleport execution but the teleport engine is unavailable");
        WarpsWiring.Wired wired =
                WarpsWiring.wire(ctx, persistence, engine, Optional.ofNullable(links.warpEconomy), bus, guiLayouts);
        links.warpEditorView = wired.editorView();
        links.warpPlayerWarpHandle = wired.playerWarpHandle();
        links.warpTeleportRegistry = wired.teleportRegistry();
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.warps(new RepositoryWarpsPlaceholders(wired.listWarps()));
        // Open the same /warp list browse menu from the management hub, resolving the viewer's visible warps
        // through the identical ListWarps filter the command uses, gated by the existing warp-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "warps",
                com.uxplima.uxmessentials.warps.application.WarpsMessageKey.WARP_MENU_TITLE,
                Material.ENDER_PEARL,
                "uxmessentials.warp.use",
                (player, viewer) ->
                        wired.warpMenu().open(player, viewer, wired.listWarps().available(viewer))));
    }

    private static void wireKits(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // kits need no database: definitions live in modules/kits/kits/<id>.conf and claim/cooldown state
        // is transient PDC.
        // The per-kit cost charges through the economy bridge captured during economy wiring when present.
        KitsWiring.Wired wired = KitsWiring.wire(plugin, ctx, Optional.ofNullable(links.kitEconomy), guiLayouts);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.kits(new KitAccessPlaceholders(wired.repository(), wired.access(), wired.listKits()));
        // Open the same /kit browse menu from the management hub, resolving the viewer's available kits through
        // the identical ListKits filter the command uses, gated by the existing kit-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "kits",
                com.uxplima.uxmessentials.kits.application.KitsMessageKey.KIT_MENU_TITLE,
                Material.CHEST,
                "uxmessentials.kit.use",
                (player, viewer) ->
                        wired.kitMenu().open(player, viewer, wired.listKits().available(viewer))));
    }

    private static void wirePlayerstate(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // playerstate's only durable state is the per-day playtime ledger behind /playtime, built over
        // persistence.dsl(); the per-player snapshot map stays transient in-memory and all live-player
        // reconciliation routes through the kernel Scheduler port onto the owning region thread. The AFK-aware
        // playtime sampler is armed below and stopped on module disable, leaving no orphaned tick.
        PlaytimeRepository playtimeRepository = PlaytimeRepositories.jooq(persistence);
        PlayerstateWiring.Wired wired = PlayerstateWiring.wire(plugin, ctx, playtimeRepository);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.playerstate(new StorePlayerstatePlaceholders(wired.store(), wired.info()));
        // Captured for staff (wired last), which binds its EXAMINE gadget to this /invsee open use case.
        links.staffOpenContainer = wired.services().openContainer();
        // Captured so presence (wired later) rebinds the playtime sampler's AFK seam to its live store, so the
        // sampler splits active vs AFK seconds. Until then — or when presence is disabled — it counts all active.
        links.playtimeAfkStatus = wired.afkStatus();
    }

    private static void wireMessaging(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // messaging builds its jOOQ mail/ignore stores over persistence.dsl() and its transient reply /
        // socialspy / toggle stores in-memory/PDC. The mute gate starts on MutePolicy.NEVER and is captured
        // here so moderation rebinds it when it lands; the vanish gate degrades to "fully visible" without
        // presence.
        // The management GUIs consume the SP0 framework: a GuiText over the shared catalog, the data-folder layout
        // loader, and the shared anvil (installed once in wireModules) for the ignore-list add prompt.
        // /msgsettings opens the settings panel; /ignore and /mail with no args open the ignore-list and mailbox.
        GuiText guiText = new GuiText(ctx.kernel().messages());
        MessagingWiring.Wired wired =
                MessagingWiring.wire(plugin, ctx, persistence, Optional.empty(), bus, guiText, guiLayouts, anvil);
        wired.commands().forEach(resources::addCommand);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.mutePolicy = wired.mutePolicy();
        links.afkStatus = wired.afkStatus();
        // The messaging PAPI seam reads the same mail/conversation/toggle/socialspy/ignore stores the messaging
        // commands hold, so a placeholder matches the player's in-game mail count and toggle state.
        links.placeholders.messaging(wired.placeholders());
        // Register two /uxmess gui hub entries — the settings panel and the mailbox — gated by the messaging GUI
        // node. The ignore-list opens from /ignore with no args; it is not a hub entry of its own.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "messaging-settings",
                com.uxplima.uxmessentials.messaging.application.MessagingMessageKey.GUI_SETTINGS_TITLE,
                Material.WRITABLE_BOOK,
                "uxmessentials.messaging.gui",
                (player, viewer) -> wired.guiViews().openSettings(player, viewer)));
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "messaging-mailbox",
                com.uxplima.uxmessentials.messaging.application.MessagingMessageKey.GUI_MAIL_TITLE,
                Material.PAPER,
                "uxmessentials.messaging.gui",
                (player, viewer) -> wired.guiViews().openMailbox(player, viewer)));
        // Captured for staff (wired last), which binds its staff chat to the messaging staff-audience resolver.
        links.staffAudience = new com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience();
    }

    private static void wireModeration(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // moderation builds its jOOQ ModerationRepository over persistence.dsl(), the audit logger on the
        // dedicated audit channel, and the login/join/freeze listeners. It rebinds the messaging mute gate and
        // the teleport jail gate captured during their wiring to the real policies — when either context is
        // disabled its holder is absent, so the bind is a no-op and that gate stays NEVER. It opts into
        // cross-server live enforcement through the bus handle: a ban on a peer kicks the player here if they
        // are online (the durable ban is already enforced on every backend's login regardless of the bus).
        // The management GUI consumes the SP0 framework (a GuiText over the shared catalog, the data-folder
        // layout loader): /mod with no args and the /uxmess gui hub both open the active-punishments list.
        ModerationWiring.GateSinks gates =
                new ModerationWiring.GateSinks(policy -> bindMute(links, policy), gate -> bindJail(links, gate));
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(
                        ctx.kernel().messages());
        ModerationWiring.Wired wired =
                ModerationWiring.wire(plugin, ctx, persistence, gates, bus, guiText, guiLayouts, anvil);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.moderation(new GateModerationPlaceholders(
                wired.mutePolicy(), wired.jailGate(), wired.repository(), wired.sanctions(), wired.clock()));
        // Register the moderation management GUI on the /uxmess gui hub, gated by the moderation GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "moderation",
                com.uxplima.uxmessentials.moderation.application.ModerationMessageKey.MOD_GUI_LIST_TITLE,
                org.bukkit.Material.IRON_BARS,
                "uxmessentials.moderation.gui",
                (player, viewer) -> wired.guiViews().open(player, viewer)));
        // Captured for staff (wired last), which binds its FREEZE gadget to the audited freeze use case and the
        // live freeze-state read (BukkitSanctions is the Sanctions adapter).
        links.staffModerationFreeze = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.ModerationFreezeSeam(
                wired.freeze(), wired.sanctions());
    }

    private static void wireItemworld(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // itemworld persists nothing: it is the full item/world command surface as stateless ACL-thin
        // mutations validated at the adapter boundary and applied through the kernel Scheduler. The only runtime
        // state is the powertool/unlimited per-player toggles and the item-PDC powertool bindings, all transient
        // and dropped with the wiring on module stop. /repair /repairall /hat /more are owned here (playerstate
        // deferred them, §15.6), so they register here and the two modules never double-register.
        ItemworldWiring.Wired wired = ItemworldWiring.wire(plugin, ctx, guiLayouts);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // Register the itemworld utilities hub on the /uxmess gui hub, gated by the itemworld GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "itemworld",
                com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey.GUI_HUB_TITLE,
                Material.CRAFTING_TABLE,
                "uxmessentials.itemworld.gui",
                (player, viewer) -> wired.hubView().open(player, viewer)));
    }

    private static void wireVaults(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            Bus bus,
            ContextLinks links,
            ManagementGuiRegistry guiRegistry) {
        // vaults builds its cached jOOQ VaultRepository over persistence.dsl(), the audit logger on the dedicated
        // audit channel, the inventory-holder GUI and the InventoryClose save listener. It opts into cross-server
        // sync through the bus handle — a remote vault save invalidates exactly that vault here. The economy
        // bridge captured during economy wiring is handed in so a configured vault cost can be charged; when it
        // is absent a configured cost is recorded but never charged. On stop the still-open vault windows are
        // close-and-saved before the pool closes.
        VaultsWiring.Wired wired =
                VaultsWiring.wire(plugin, ctx, persistence, bus, Optional.ofNullable(links.vaultEconomy));
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.vaults(
                new RepositoryVaultsPlaceholders(wired.repository(), wired.amountQuota(), wired.sizeQuota()));
        // Open the same /vault selector from the management hub, gated by the existing vault-use node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "vaults",
                com.uxplima.uxmessentials.vaults.application.VaultsMessageKey.VAULT_SELECTOR_TITLE,
                Material.ENDER_CHEST,
                "uxmessentials.vault.use",
                (player, viewer) -> wired.selector().open(player, viewer)));
    }

    private static void bindMute(
            ContextLinks links, com.uxplima.uxmessentials.messaging.application.port.MutePolicy policy) {
        MutableMutePolicy holder = links.mutePolicy;
        if (holder != null) {
            holder.bind(policy);
        }
    }

    private static void bindJail(
            ContextLinks links, com.uxplima.uxmessentials.teleport.application.port.JailGate gate) {
        MutableJailGate holder = links.jailGate;
        if (holder != null) {
            holder.bind(gate);
        }
    }

    private static void bindAfk(
            ContextLinks links, com.uxplima.uxmessentials.messaging.application.port.AfkStatus status) {
        MutableAfkStatus holder = links.afkStatus;
        if (holder != null) {
            holder.bind(status);
        }
    }

    private static void bindPlaytimeAfk(
            ContextLinks links, com.uxplima.uxmessentials.playerstate.application.port.AfkStatus status) {
        com.uxplima.uxmessentials.playerstate.adapter.outbound.MutablePlaytimeAfkStatus holder =
                links.playtimeAfkStatus;
        if (holder != null) {
            holder.bind(status);
        }
    }

    private static void wirePresence(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // presence persists nothing: the per-player PlayerPresence map is transient in-memory state. Its
        // BukkitVisibilityApplier drives the canSee graph that messaging's /msg resolution and teleport's /tpa
        // listing already read, so the vanish soft-couple needs no extra cross-context handle wired here. The
        // AFK soft-couple does need one: presence rebinds messaging's MutableAfkStatus (captured during the
        // earlier messaging wiring) to a PresenceAfkStatus over this store so /msg adds the AFK courtesy
        // notice. When messaging is disabled the holder is absent and the bind is a no-op.
        PresenceWiring.Wired wired = PresenceWiring.wire(plugin, ctx, guiLayouts, guiRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.presence(new StorePresencePlaceholders(wired.store(), wired.clock()));
        bindAfk(links, new PresenceAfkStatus(wired.store()));
        // Rebind the playtime sampler's AFK seam (captured during the earlier playerstate wiring) to the live
        // presence store, so the sampler splits each player's seconds into active vs AFK. When playerstate is
        // disabled the holder is absent and this is a no-op.
        bindPlaytimeAfk(
                links, new com.uxplima.uxmessentials.playerstate.adapter.outbound.PresenceAfkStatus(wired.store()));
        // Captured for staff (wired last), which binds its VANISH gadget and vanish-on-enter to this toggle + store.
        links.staffPresenceVanish = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.PresenceVanishSeam(
                wired.services().toggleVanish(), wired.store());
    }

    private static void wireCommunication(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // communication's only durable state is the DB-backed announcement set the /announce editor owns, built
        // over persistence.dsl(); the per-player broadcast opt-out is PDC-backed (survives relog), the sequence
        // counters are transient, and the connection policies, file announcer schedule, and info pages are
        // config-authored content. The announcer rotates over the file config PLUS the enabled store set. It carries
        // no cross-context bridge, so nothing is captured for a later context. The announcer timer on the Scheduler
        // port is stopped on disable.
        // The admin panel and the announcement editor consume the SP0 GUI framework: the data-folder layout loader
        // plus the shared anvil (installed once in wireModules) for the broadcast and editor prompts. /communication
        // gui and the /uxmess gui hub open the admin panel; bare /announce opens the editor.
        AnnouncementStore announcementStore = AnnouncementStores.jooq(persistence);
        com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore announcerSettingsStore =
                AnnouncementStores.settings(persistence);
        CommunicationWiring.Wired wired =
                CommunicationWiring.wire(plugin, ctx, announcementStore, announcerSettingsStore, guiLayouts, anvil);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        // The communication PAPI seam reads the same global chat lock /togglechat flips and the per-player announcer
        // subscription /broadcasttoggle flips, so a placeholder matches the live chat state and the player's opt-in.
        links.placeholders.communication(new StoreCommunicationPlaceholders(wired.chatLock(), wired.optOutStore()));
        resources.onClose(wired::stop);
        // Register the communication admin panel on the /uxmess gui hub, gated by the communication GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "communication",
                com.uxplima.uxmessentials.communication.application.CommunicationMessageKey.GUI_PANEL_TITLE,
                Material.WRITABLE_BOOK,
                "uxmessentials.communication.gui",
                (player, viewer) -> wired.adminView().open(player, viewer)));
    }

    private static void wireHolograms(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // holograms builds its cached jOOQ HologramRepository over persistence.dsl() and its renderer over the
        // uxmLib native-Display API; the holograms / hologram_lines tables ship in the persistence V13 baseline,
        // always applied. Its one cross-context bridge is the leaderboard data-source registry: the economy module
        // (when enabled, wired earlier) publishes a balance provider onto the links, which a leaderboard hologram
        // renders; with economy disabled the registry is empty and a leaderboard reads "(no data)". On wire every
        // stored hologram is spawned (each on its own region thread); on disable they are despawned cleanly.
        LeaderboardProviders leaderboards = new LeaderboardProviders(
                links.balanceLeaderboard == null ? Map.of() : Map.of("balance", links.balanceLeaderboard));
        // The same shared economy bridge npc charges its COST click actions through (captured during economy
        // wiring, which lands long before holograms); absent on a server without economy, so a hologram COST gate
        // is simply skipped there. It is a generic ClickActionEconomy, not an npc handle — holograms reaches no npc.
        // The management GUI consumes the SP0 framework: a GuiText over the shared catalog and the data-folder
        // layout loader (disk-first, then bundled). The list view (built inside HologramsWiring with the
        // repository + use cases) opens for /hologram with no args and from the /uxmess gui hub.
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(
                        ctx.kernel().messages());
        HologramsWiring.Wired wired = HologramsWiring.wire(
                plugin,
                ctx,
                persistence,
                bus,
                leaderboards,
                Optional.ofNullable(links.npcEconomy),
                guiText,
                guiLayouts,
                anvil);
        wired.commands().forEach(resources::addCommand);
        // The holograms PAPI seam reads the same cached repository /hologram list shows, so the count placeholder
        // matches the registered hologram total (a server-wide value resolved per request).
        links.placeholders.holograms(new RepositoryHologramsPlaceholders(wired.repository()));
        // Register the holograms management GUI on the /uxmess gui hub, gated by the holograms GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "holograms",
                com.uxplima.uxmessentials.holograms.application.HologramsMessageKey.HOLOGRAM_GUI_LIST_TITLE,
                org.bukkit.Material.ARMOR_STAND,
                "uxmessentials.holograms.gui",
                (player, viewer) -> wired.listView().open(player, viewer)));
        resources.onClose(wired::stop);
    }

    private static void wirePlayerwarps(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // player-warps delegates teleport execution to the captured teleport engine exactly as warps does; its
        // cached jOOQ repository over persistence.dsl() is keyed per owner, and the player_warps table ships in
        // the persistence V14 baseline, always applied. It carries no cross-context bridge beyond the engine.
        // The management GUI consumes the SP0 framework (a GuiText over the shared catalog, the data-folder layout
        // loader, an anvil): /pwarp with no args opens an owner-scoped list (a player sees their own warps, an
        // operator holding uxmessentials.pwarp.gui sees and manages everyone's) → per-warp editor, and it
        // registers the /uxmess gui hub entry. The existing /pwarp edit warps-editor reuse is untouched.
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine,
                "playerwarps delegates teleport execution but the teleport engine is unavailable");
        GuiText guiText = new GuiText(ctx.kernel().messages());
        PlayerwarpsWiring.Wired wired = PlayerwarpsWiring.wire(
                plugin,
                ctx,
                persistence,
                engine,
                bus,
                links.warpEditorView,
                links.warpPlayerWarpHandle,
                links.warpTeleportRegistry,
                guiText,
                guiLayouts,
                anvil,
                guiRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The player-warps PAPI seam reads the same cached repository and count-limit quota the /pwarp commands
        // hold, so a placeholder matches what /setpwarp enforces and the /pwarps list shows.
        links.placeholders.playerwarps(new RepositoryPlayerwarpsPlaceholders(wired.repository(), wired.quota()));
    }

    private static void wireScoreboard(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // scoreboard persists nothing: the per-player "hidden" bit is PDC-backed (survives relog) and the sidebar /
        // tablist content is config-authored under modules/scoreboard/config.conf. Its one cross-context handle is the
        // shared NameVisibilityCoordinator: the SidebarManager re-applies the vanilla-name-hide team after every board
        // switch through it (the setScoreboard team-registry reset would otherwise drop it), so a nametag wearer keeps
        // their name hidden across board switches. The renderer dogfoods uxmlib-hud's SidebarManager; the render timer
        // on the Scheduler port is stopped and every active board torn down on disable. The settings panel consumes
        // the SP0 GUI framework (a GuiText over the shared catalog, the data-folder layout loader) and registers its
        // /uxmess gui hub entry; /scoreboard gui opens the same single-toggle panel, gated on the GUI node.
        ScoreboardWiring.Wired wired = ScoreboardWiring.wire(plugin, ctx, links.nameVisibility, guiLayouts);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The scoreboard PAPI seam reads the same PDC-backed "hidden" bit the /scoreboard toggle flips, so the
        // scoreboard_visible placeholder matches whether the player actually sees the sidebar in game.
        links.placeholders.scoreboard(new StoreScoreboardPlaceholders(wired.visibility()));
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        // Register the scoreboard settings panel on the /uxmess gui hub, gated by the player-facing GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "scoreboard",
                com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey.GUI_TITLE,
                Material.PAINTING,
                "uxmessentials.scoreboard.gui",
                (player, viewer) -> wired.settingsView().open(player, viewer)));
    }

    private static void wireTablist(JavaPlugin plugin, ModuleContext ctx, CloseableResources resources) {
        // tablist persists nothing: the header/footer content is config-authored under modules/tablist/config.conf. It
        // carries no cross-context bridge — its only collaborators are the shared Scheduler and log ports — so nothing
        // is captured for a later context, and the tablist is always-on (no per-player toggle) so it publishes no
        // command. The renderer dogfoods uxmlib-hud's Tablist; the render timer on the Scheduler port is stopped and
        // every active header/footer cleared on disable.
        TablistWiring.Wired wired = TablistWiring.wire(plugin, ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
    }

    private static void wireNametags(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // nametags persists nothing: the per-wearer formats are config-authored under modules/nametags/config.conf. It
        // soft-couples to presence (vanish-aware viewer culling through Bukkit's canSee graph, degrading to "everyone
        // can see everyone" with presence off). Its one cross-context handle is the shared NameVisibilityCoordinator:
        // the presenter hides a wearer's vanilla above-head name through it while the custom nametag is live (and the
        // scoreboard SidebarManager re-applies that hide-team after every board switch through the same instance). The
        // nametag is always-on (no per-player toggle) so it publishes no command. Rendering goes through uxmLib's
        // packet NametagRenderer: per-viewer spawn/metadata/remove bundles with no real entity, and a per-wearer
        // refresh loop the lib owns. On disable the reconcile timer on the Scheduler port is stopped and
        // presenter.removeAll() restores every online wearer's vanilla name, sends every wearer's remove packets, and
        // cancels each lib refresh task, so a disable/reload leaves no orphan.
        NametagsWiring.Wired wired = NametagsWiring.wire(plugin, ctx, links.nameVisibility);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
    }

    private static void wireNpc(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            com.uxplima.uxmlib.gui.anvil.AnvilInput anvil) {
        // npc builds its cached jOOQ NpcRepository over persistence.dsl() and its renderer over the uxmLib NPC
        // packet stack; the npc table ships in the persistence V38 baseline, always applied. Its one cross-context
        // edge is soft: a COST click action charges through the economy bridge captured during economy wiring (npc
        // lands after economy), absent on a server without economy so the gate is simply skipped. A fake-player NPC
        // has no real entity: each viewer is sent a spawn (then its tab entry is hidden), range-culled and
        // re-evaluated on a per-second global refresh and on join/quit/world-change. On wire every stored NPC is
        // shown to the online viewers in range; on disable the refresh timer is cancelled and every shown fake
        // player is removed from every viewer so a reload re-spawns cleanly with no ghost. The management GUI
        // consumes the SP0 framework (a GuiText over the shared catalog, the data-folder layout loader, an anvil)
        // and registers its /uxmess gui hub entry; /npc with no args opens the same list, gated on the GUI node.
        GuiText guiText = new GuiText(ctx.kernel().messages());
        NpcWiring.Wired wired = NpcWiring.wire(
                plugin,
                ctx,
                persistence,
                bus,
                Optional.ofNullable(links.npcEconomy),
                guiText,
                guiLayouts,
                anvil,
                guiRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
    }

    private static void wireStaff(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // staff persists the captured loadout through the jOOQ StaffLoadoutRepository over persistence.dsl() (the
        // staff_loadout table ships in the persistence V29 baseline, always applied) — the item-loss-safe net, so
        // a crash mid-mode leaves the real loadout recoverable. It wires last, so it binds its three soft-couple
        // holders to the presence/playerstate/messaging seams captured during those contexts' wiring; a seam is
        // absent when its source module is disabled, leaving that gadget or staff chat on NONE (degrade, not fail).
        // On stop it exits every staff member still in staff mode, restoring their real loadout so a disable/reload
        // never strands anyone in the gadget hotbar.
        StaffWiring.StaffSeams seams = new StaffWiring.StaffSeams(
                Optional.ofNullable(links.staffPresenceVanish),
                Optional.ofNullable(links.staffOpenContainer),
                Optional.ofNullable(links.staffAudience),
                Optional.ofNullable(links.staffModerationFreeze),
                Optional.ofNullable(links.staffTeleport));
        // The in-process bus is the concrete publisher so the enter/exit alert subscriber can be registered here
        // and unsubscribed on stop (the kernel port exposes only publish). With messaging off no alert is wired.
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        StaffWiring.Wired wired = StaffWiring.wire(plugin, ctx, persistence, seams, events);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The staff PAPI seam reads the same staff-mode marker the /staffmode use cases hold and counts the online
        // staff-member holders the /stafflist roster shows, so a placeholder matches what the player sees in game.
        links.placeholders.staff(new StaffStaffPlaceholders(wired.services().store(), plugin.getServer()));
        resources.onClose(wired::stop);
    }

    private static void wireVote(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus) {
        // vote builds its counter-cached jOOQ VoteRepository over persistence.dsl() (the vote_party counter and
        // vote_queue offline reward batches ship in the persistence V15 baseline, always applied), the console
        // reward dispatcher on the global region thread, and the online audience for the party rewards and
        // thank-you broadcast. It syncs the server-wide party counter through the bus handle — a counter
        // mutation here announces a VoteCounterChanged, a remote one drops the cached counter, and a remote
        // VotePartyFired echoes the party announcement (never the reward) — but carries no cross-context bridge.
        // The reflective Votifier listener self-registers behind a plugin-present guard on start and is dropped
        // on disable, so the module runs unchanged whether or not Votifier is installed. The repository and
        // threshold are surfaced for the PAPI vote placeholder seam registered after all contexts have wired.
        InProcessDomainEventPublisher events =
                (InProcessDomainEventPublisher) ctx.kernel().events();
        VoteWiring.Wired wired = VoteWiring.wire(plugin, ctx, persistence, events, bus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        // Resolve leaderboard UUIDs to display names for votes_top_<period>_<n>_name — the same lookup
        // /vote top uses — so the placeholder shows a name, not the UUID the repository stores.
        com.uxplima.uxmessentials.shared.application.port.PlayerLookup lookup =
                ctx.kernel().playerLookup();
        java.util.function.Function<java.util.UUID, String> nameResolver = uuid -> lookup.findByUuid(uuid)
                .map(com.uxplima.uxmessentials.shared.domain.PlayerRef::name)
                .orElse(uuid.toString().toLowerCase(java.util.Locale.ROOT));
        links.placeholders.vote(
                new RepositoryVotePlaceholders(wired.repository(), wired.partyThreshold(), nameResolver));
    }

    private static void wireDiscordlink(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry) {
        // discordlink builds its un-cached jOOQ store over persistence.dsl() (the discord_link_pending and
        // discord_links tables ship in the persistence V16 baseline, always applied) and the /discordlink and
        // /discordunlink commands. It registers its ConfirmLink seam into the ServicesManager so the optional
        // Discord bridge — a separate jar with no compile-time link to this one — can redeem a /link code through
        // the same use case; the registration is dropped on disable so a reload re-exposes it cleanly. The bridge
        // looks the service up once its gateway is ready and forwards nothing while it is absent. The link-status
        // panel consumes the SP0 GUI framework (a GuiText over the shared catalog, the data-folder layout loader)
        // and registers its /uxmess gui hub entry; /discordlink gui opens the same panel, gated on the GUI node.
        com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge bridge =
                new com.uxplima.uxmessentials.discordlink.adapter.outbound.ServicesManagerDiscordBridge(
                        plugin.getServer().getServicesManager());
        DiscordlinkWiring.Wired wired = DiscordlinkWiring.wire(ctx, persistence, guiLayouts, bridge);
        wired.commands().forEach(resources::addCommand);
        // The discordlink PAPI seam reads the same DB-backed link store the /discordlink commands hold, so a
        // placeholder matches the binding the player redeemed (and answers for an offline player too).
        links.placeholders.discordlink(new StoreDiscordlinkPlaceholders(wired.store()));
        plugin.getServer()
                .getServicesManager()
                .register(
                        DiscordLinkConfirmation.class,
                        wired.confirmation(),
                        plugin,
                        org.bukkit.plugin.ServicePriority.Normal);
        resources.onClose(() -> plugin.getServer().getServicesManager().unregister(wired.confirmation()));
        // Register the discordlink link-status panel on the /uxmess gui hub, gated by the player-facing GUI node.
        guiRegistry.register(new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(
                "discordlink",
                com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey.GUI_TITLE,
                Material.PLAYER_HEAD,
                "uxmessentials.discord.gui",
                (player, viewer) -> wired.view().open(player, viewer)));
    }

    /** Cross-context handles captured during wiring so a dependent context reaches its prerequisite. */
    private static final class ContextLinks {
        private @org.jspecify.annotations.Nullable TeleportEngine teleportEngine;
        // The live economy provider and default currency, captured during economy wiring (which lands after
        // worlds). worlds resolves them lazily at fee-charge time, so a null here simply means "free worlds".
        private com.uxplima.uxmessentials.economy.application.port.@org.jspecify.annotations.Nullable EconomyProvider
                economyProvider;
        private com.uxplima.uxmessentials.economy.domain.@org.jspecify.annotations.Nullable Currency economyCurrency;
        private @org.jspecify.annotations.Nullable WarpEconomy warpEconomy;
        private @org.jspecify.annotations.Nullable KitEconomy kitEconomy;
        private @org.jspecify.annotations.Nullable HomeEconomy homeEconomy;
        private com.uxplima.uxmessentials.vaults.application.port.@org.jspecify.annotations.Nullable VaultEconomy
                vaultEconomy;
        private @org.jspecify.annotations.Nullable ClickActionEconomy npcEconomy;
        private com.uxplima.uxmessentials.holograms.application.port.@org.jspecify.annotations.Nullable LeaderboardProvider
                balanceLeaderboard;
        private @org.jspecify.annotations.Nullable MutableMutePolicy mutePolicy;
        private @org.jspecify.annotations.Nullable MutableAfkStatus afkStatus;
        private com.uxplima.uxmessentials.playerstate.adapter.outbound.@org.jspecify.annotations.Nullable MutablePlaytimeAfkStatus
                playtimeAfkStatus;
        private @org.jspecify.annotations.Nullable MutableJailGate jailGate;
        private @org.jspecify.annotations.Nullable MutableHomeRespawnLocator homeRespawnLocator;
        private com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                warpEditorView;
        private com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpRepositoryHandle
                warpPlayerWarpHandle;
        private com.uxplima.uxmessentials.warps.adapter.@org.jspecify.annotations.Nullable WarpTeleportRegistry
                warpTeleportRegistry;
        // The soft-couple seams staff binds when it wires (it lands last). Each is captured during the source
        // context's wiring and left null when that context is disabled, so staff degrades the matching gadget or
        // staff chat to a no-op rather than failing.
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable PresenceVanishSeam
                staffPresenceVanish;
        private com.uxplima.uxmessentials.playerstate.application.@org.jspecify.annotations.Nullable OpenContainer
                staffOpenContainer;
        private com.uxplima.uxmessentials.messaging.application.port.@org.jspecify.annotations.Nullable StaffAudience
                staffAudience;
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable ModerationFreezeSeam
                staffModerationFreeze;
        private com.uxplima.uxmessentials.staff.adapter.StaffWiring.@org.jspecify.annotations.Nullable TeleportSeam
                staffTeleport;
        // The PlaceholderAPI read seams, filled by each enabled context that contributes placeholders.
        private final PlaceholderContexts.Builder placeholders = PlaceholderContexts.builder();
        // Built once and shared by the scoreboard and nametags wirings (in either registry order): the nametags
        // presenter hides a wearer's vanilla name through it, the scoreboard SidebarManager re-applies the hide-team
        // after every board switch through it. Inert until a nametags hide call marks a player, so it costs nothing
        // when either module is off.
        private final NameVisibilityCoordinator nameVisibility = new NameVisibilityCoordinator();
    }

    private static boolean skippedByCapability(FeatureModule module, ModuleContext ctx, Logger log) {
        LoadCondition condition = module.loadCondition();
        Optional<String> unmet = condition.unmetReason(ctx);
        if (unmet.isPresent()) {
            log.warning("module " + module.id() + " skipped — " + unmet.get());
            return true;
        }
        return false;
    }

    private static void startModule(FeatureModule module, ModuleContext ctx, CloseableResources resources, Logger log) {
        // Migrations for every enabled, loadable module were applied up front when the persistence layer
        // opened (see KernelWiring.openPersistence). A disabled module contributes no location, so its
        // tables stay absent. By the time start() runs the schema is already in place.
        long startedAt = System.nanoTime();
        module.start(ctx);
        long loadMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        resources.onClose(module::stop);
        log.info("module " + module.id() + " loaded in " + loadMillis + " ms");
    }
}
