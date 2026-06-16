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

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.bootstrap.CommandAliasDefaults;
import com.uxplima.uxmessentials.bootstrap.command.BackupCommand;
import com.uxplima.uxmessentials.bootstrap.command.HelpCommand;
import com.uxplima.uxmessentials.bootstrap.command.LangCommand;
import com.uxplima.uxmessentials.bootstrap.command.MigrationImportNode;
import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.bootstrap.health.DatabaseHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.EconomyProviderHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.ModuleCountHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SchedulerHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.SoftDependencyHealthCheck;
import com.uxplima.uxmessentials.bootstrap.health.UpdateHealthCheck;
import com.uxplima.uxmessentials.communication.adapter.CommunicationWiring;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordlinkWiring;
import com.uxplima.uxmessentials.economy.adapter.EconomyWiring;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.holograms.adapter.HologramsWiring;
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
import com.uxplima.uxmessentials.npc.application.port.NpcEconomy;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerstateWiring;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerwarpsWiring;
import com.uxplima.uxmessentials.presence.adapter.PresenceWiring;
import com.uxplima.uxmessentials.scoreboard.adapter.ScoreboardWiring;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CatalogBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
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
import com.uxplima.uxmessentials.shared.application.command.CommandOverride;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
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
                wireModules(plugin, registry, config, kernel, persistence, resources, log, bus.bus());
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
        List<HealthCheck> healthChecks = healthChecks(plugin, registry, config, persistence);
        resources.addCommand(new UxmessCommand(registry, config, importNode, kernel.scheduler(), healthChecks));
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
        Map<String, CommandOverride> overrides =
                new CommandCatalogConfig(plugin.getDataFolder().toPath(), kernel.log()).load();
        CommandCatalog.Resolution resolution = CommandCatalog.resolve(defs, overrides);
        resolution.warnings().forEach(w -> kernel.log().warn("command catalog: {}", w.message()));
        // Seed the editable file from the resolved surface so a fresh install lands a self-documenting
        // commands.conf instead of an empty folder the catalog would silently ignore.
        writeDefaultCatalog(plugin, resolution.effective(), kernel.log());
        Map<String, EffectiveCommand> byId =
                resolution.effective().stream().collect(Collectors.toMap(e -> e.id().value(), e -> e));
        resources.catalogBinding(new CatalogBinding(byId));
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
            JavaPlugin plugin, ModuleRegistry registry, ConfigStore config, Persistence persistence) {
        // Assembled after the modules are wired so the set reflects what is actually present: the database and
        // soft-depend probes always apply, the economy-provider ownership check only when economy is enabled.
        // The /uxmess doctor command runs each one off-tick (each is wrapped in HealthCheck.safe so a probe that
        // throws becomes a FAIL line rather than aborting the run).
        ConfigStore economyConfig = config.scoped(ModuleId.of("economy").configRoot());
        List<HealthCheck> checks = new ArrayList<>();
        checks.add(new DatabaseHealthCheck(persistence));
        if (economyEnabled(registry, config)) {
            checks.add(new EconomyProviderHealthCheck(plugin.getServer().getServicesManager(), plugin));
        }
        checks.add(new SoftDependencyHealthCheck(plugin.getServer().getPluginManager(), economyConfig));
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
            Bus bus) {
        // teleport is wired before homes/warps (registry order is dependency-first), so its engine is
        // captured and handed to the contexts that delegate teleport execution to it.
        ContextLinks links = new ContextLinks();
        // Install uxmLib's single menu listener once, before any GUI-using module (vaults, itemworld) wires,
        // and tear it down on disable so a reload re-installs cleanly (the static install state is reset).
        Guis.install(plugin);
        resources.onClose(Guis::uninstall);
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
            wireAdapters(plugin, module, ctx, persistence, resources, links, bus, guiLayouts);
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
            GuiLayouts guiLayouts) {
        // The bukkit-side adapters of each context are wired here once the context's pure module has
        // started. teleport builds its durable jOOQ spawn directory over persistence.dsl(); homes builds
        // its jOOQ repository the same way and delegates execution to the captured teleport engine.
        if (module.id().equals(ModuleId.of("teleport"))) {
            wireTeleport(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("homes"))) {
            wireHomes(plugin, ctx, persistence, resources, links, bus, guiLayouts);
        } else if (module.id().equals(ModuleId.of("economy"))) {
            wireEconomy(plugin, ctx, persistence, resources, links, bus);
        } else if (module.id().equals(ModuleId.of("warps"))) {
            wireWarps(ctx, persistence, resources, links, bus, guiLayouts);
        } else if (module.id().equals(ModuleId.of("kits"))) {
            wireKits(plugin, ctx, resources, links, guiLayouts);
        } else if (module.id().equals(ModuleId.of("playerstate"))) {
            wirePlayerstate(ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("messaging"))) {
            wireMessaging(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("presence"))) {
            wirePresence(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("moderation"))) {
            wireModeration(plugin, ctx, persistence, resources, links, bus);
        } else if (module.id().equals(ModuleId.of("itemworld"))) {
            wireItemworld(plugin, ctx, resources, guiLayouts);
        } else if (module.id().equals(ModuleId.of("vaults"))) {
            wireVaults(plugin, ctx, persistence, resources, bus, links);
        } else if (module.id().equals(ModuleId.of("communication"))) {
            wireCommunication(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("holograms"))) {
            wireHolograms(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("playerwarps"))) {
            wirePlayerwarps(ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("scoreboard"))) {
            wireScoreboard(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("tablist"))) {
            wireTablist(plugin, ctx, resources);
        } else if (module.id().equals(ModuleId.of("vote"))) {
            wireVote(plugin, ctx, persistence, resources, links, bus);
        } else if (module.id().equals(ModuleId.of("discordlink"))) {
            wireDiscordlink(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("nametags"))) {
            wireNametags(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("staff"))) {
            wireStaff(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("npc"))) {
            wireNpc(plugin, ctx, persistence, resources, links);
        }
    }

    private static void wireTeleport(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        TeleportWiring.Wired wired = TeleportWiring.wire(plugin, ctx, persistence);
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

    private static void wireHomes(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts) {
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
            Bus bus) {
        EconomyWiring.Wired wired = EconomyWiring.wire(plugin, ctx, persistence, bus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.start();
        resources.onClose(wired::stop);
        // Captured for warps, kits, homes, and vaults, which land after economy and charge a recorded cost
        // through it.
        links.warpEconomy = wired.warpEconomy();
        links.kitEconomy = wired.kitEconomy();
        links.homeEconomy = wired.homeEconomy();
        links.vaultEconomy = wired.vaultEconomy();
        links.npcEconomy = wired.npcEconomy();
        links.placeholders.economy(new ProviderEconomyPlaceholders(
                wired.provider(), wired.defaultCurrency(), wired.amountFormat(), BalTop.MAX_PAGE_SIZE));
    }

    private static void wireWarps(
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus,
            GuiLayouts guiLayouts) {
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
    }

    private static void wireKits(
            JavaPlugin plugin,
            ModuleContext ctx,
            CloseableResources resources,
            ContextLinks links,
            GuiLayouts guiLayouts) {
        // kits need no database: definitions live in modules/kits/kits/<id>.conf and claim/cooldown state
        // is transient PDC.
        // The per-kit cost charges through the economy bridge captured during economy wiring when present.
        KitsWiring.Wired wired = KitsWiring.wire(plugin, ctx, Optional.ofNullable(links.kitEconomy), guiLayouts);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.kits(new KitAccessPlaceholders(wired.repository(), wired.access(), wired.listKits()));
    }

    private static void wirePlayerstate(ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // playerstate persists nothing: the per-player snapshot map is transient in-memory state, and all
        // live-player reconciliation routes through the kernel Scheduler port onto the owning region thread.
        PlayerstateWiring.Wired wired = PlayerstateWiring.wire(ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.playerstate(new StorePlayerstatePlaceholders(wired.store(), wired.info()));
        // Captured for staff (wired last), which binds its EXAMINE gadget to this /invsee open use case.
        links.staffOpenContainer = wired.services().openContainer();
    }

    private static void wireMessaging(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // messaging builds its jOOQ mail/ignore stores over persistence.dsl() and its transient reply /
        // socialspy / toggle stores in-memory/PDC. The mute gate starts on MutePolicy.NEVER and is captured
        // here so moderation rebinds it when it lands; the vanish gate degrades to "fully visible" without
        // presence.
        MessagingWiring.Wired wired = MessagingWiring.wire(plugin, ctx, persistence, Optional.empty());
        wired.commands().forEach(resources::addCommand);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.mutePolicy = wired.mutePolicy();
        links.afkStatus = wired.afkStatus();
        // The messaging PAPI seam reads the same mail/conversation/toggle/socialspy/ignore stores the messaging
        // commands hold, so a placeholder matches the player's in-game mail count and toggle state.
        links.placeholders.messaging(wired.placeholders());
        // Captured for staff (wired last), which binds its staff chat to the messaging staff-audience resolver.
        links.staffAudience = new com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience();
    }

    private static void wireModeration(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links,
            Bus bus) {
        // moderation builds its jOOQ ModerationRepository over persistence.dsl(), the audit logger on the
        // dedicated audit channel, and the login/join/freeze listeners. It rebinds the messaging mute gate and
        // the teleport jail gate captured during their wiring to the real policies — when either context is
        // disabled its holder is absent, so the bind is a no-op and that gate stays NEVER. It opts into
        // cross-server live enforcement through the bus handle: a ban on a peer kicks the player here if they
        // are online (the durable ban is already enforced on every backend's login regardless of the bus).
        ModerationWiring.GateSinks gates =
                new ModerationWiring.GateSinks(policy -> bindMute(links, policy), gate -> bindJail(links, gate));
        ModerationWiring.Wired wired = ModerationWiring.wire(plugin, ctx, persistence, gates, bus);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        resources.onClose(wired::stop);
        links.placeholders.moderation(new GateModerationPlaceholders(
                wired.mutePolicy(), wired.jailGate(), wired.repository(), wired.sanctions(), wired.clock()));
        // Captured for staff (wired last), which binds its FREEZE gadget to the audited freeze use case and the
        // live freeze-state read (BukkitSanctions is the Sanctions adapter).
        links.staffModerationFreeze = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.ModerationFreezeSeam(
                wired.freeze(), wired.sanctions());
    }

    private static void wireItemworld(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, GuiLayouts guiLayouts) {
        // itemworld persists nothing: it is the full EssentialsX item/world surface as stateless ACL-thin
        // mutations validated at the adapter boundary and applied through the kernel Scheduler. The only runtime
        // state is the powertool/unlimited per-player toggles and the item-PDC powertool bindings, all transient
        // and dropped with the wiring on module stop. /repair /repairall /hat /more are owned here (playerstate
        // deferred them, §15.6), so they register here and the two modules never double-register.
        ItemworldWiring.Wired wired = ItemworldWiring.wire(plugin, ctx, guiLayouts);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
    }

    private static void wireVaults(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            Bus bus,
            ContextLinks links) {
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

    private static void wirePresence(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // presence persists nothing: the per-player PlayerPresence map is transient in-memory state. Its
        // BukkitVisibilityApplier drives the canSee graph that messaging's /msg resolution and teleport's /tpa
        // listing already read, so the vanish soft-couple needs no extra cross-context handle wired here. The
        // AFK soft-couple does need one: presence rebinds messaging's MutableAfkStatus (captured during the
        // earlier messaging wiring) to a PresenceAfkStatus over this store so /msg adds the AFK courtesy
        // notice. When messaging is disabled the holder is absent and the bind is a no-op.
        PresenceWiring.Wired wired = PresenceWiring.wire(plugin, ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.placeholders.presence(new StorePresencePlaceholders(wired.store(), wired.clock()));
        bindAfk(links, new PresenceAfkStatus(wired.store()));
        // Captured for staff (wired last), which binds its VANISH gadget and vanish-on-enter to this toggle + store.
        links.staffPresenceVanish = new com.uxplima.uxmessentials.staff.adapter.StaffWiring.PresenceVanishSeam(
                wired.services().toggleVanish(), wired.store());
    }

    private static void wireCommunication(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // communication persists nothing: the per-player broadcast opt-out is PDC-backed (survives relog), the
        // sequence counters are transient, and the connection policies, announcer schedule, and info pages are
        // config-authored content in the modules/communication content files. It carries no cross-context bridge — its
        // only
        // collaborators are the shared Scheduler, messages/messageSink, and event ports — so nothing is captured
        // for a later context. The announcer timer on the Scheduler port is stopped on disable.
        CommunicationWiring.Wired wired = CommunicationWiring.wire(plugin, ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        // The communication PAPI seam reads the same global chat lock /togglechat flips and the per-player announcer
        // subscription /broadcasttoggle flips, so a placeholder matches the live chat state and the player's opt-in.
        links.placeholders.communication(new StoreCommunicationPlaceholders(wired.chatLock(), wired.optOutStore()));
        resources.onClose(wired::stop);
    }

    private static void wireHolograms(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // holograms builds its cached jOOQ HologramRepository over persistence.dsl() and its renderer over the
        // uxmLib native-Display API; the holograms / hologram_lines tables ship in the persistence V13 baseline,
        // always applied. It carries no cross-context bridge — its only collaborators are the shared Scheduler,
        // messages/messageSink, and event ports. On wire every stored hologram is spawned (each on its own region
        // thread); on disable the spawned display entities are despawned so a reload re-spawns cleanly.
        HologramsWiring.Wired wired = HologramsWiring.wire(plugin, ctx, persistence);
        wired.commands().forEach(resources::addCommand);
        // The holograms PAPI seam reads the same cached repository /hologram list shows, so the count placeholder
        // matches the registered hologram total (a server-wide value resolved per request).
        links.placeholders.holograms(new RepositoryHologramsPlaceholders(wired.repository()));
        resources.onClose(wired::stop);
    }

    private static void wirePlayerwarps(
            ModuleContext ctx, Persistence persistence, CloseableResources resources, ContextLinks links) {
        // player-warps delegates teleport execution to the captured teleport engine exactly as warps does; its
        // cached jOOQ repository over persistence.dsl() is keyed per owner, and the player_warps table ships in
        // the persistence V14 baseline, always applied. It carries no cross-context bridge beyond the engine.
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine,
                "playerwarps delegates teleport execution but the teleport engine is unavailable");
        PlayerwarpsWiring.Wired wired = PlayerwarpsWiring.wire(
                ctx, persistence, engine, links.warpEditorView, links.warpPlayerWarpHandle, links.warpTeleportRegistry);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The player-warps PAPI seam reads the same cached repository and count-limit quota the /pwarp commands
        // hold, so a placeholder matches what /setpwarp enforces and the /pwarps list shows.
        links.placeholders.playerwarps(new RepositoryPlayerwarpsPlaceholders(wired.repository(), wired.quota()));
    }

    private static void wireScoreboard(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        // scoreboard persists nothing: the per-player "hidden" bit is PDC-backed (survives relog) and the sidebar /
        // tablist content is config-authored under modules/scoreboard/config.conf. Its one cross-context handle is the
        // shared NameVisibilityCoordinator: the SidebarManager re-applies the vanilla-name-hide team after every board
        // switch through it (the setScoreboard team-registry reset would otherwise drop it), so a nametag wearer keeps
        // their name hidden across board switches. The renderer dogfoods uxmlib-hud's SidebarManager; the render timer
        // on the Scheduler port is stopped and every active board torn down on disable.
        ScoreboardWiring.Wired wired = ScoreboardWiring.wire(plugin, ctx, links.nameVisibility);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        // The scoreboard PAPI seam reads the same PDC-backed "hidden" bit the /scoreboard toggle flips, so the
        // scoreboard_visible placeholder matches whether the player actually sees the sidebar in game.
        links.placeholders.scoreboard(new StoreScoreboardPlaceholders(wired.visibility()));
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
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
            ContextLinks links) {
        // npc builds its cached jOOQ NpcRepository over persistence.dsl() and its renderer over the uxmLib NPC
        // packet stack; the npc table ships in the persistence V38 baseline, always applied. Its one cross-context
        // edge is soft: a COST click action charges through the economy bridge captured during economy wiring (npc
        // lands after economy), absent on a server without economy so the gate is simply skipped. A fake-player NPC
        // has no real entity: each viewer is sent a spawn (then its tab entry is hidden), range-culled and
        // re-evaluated on a per-second global refresh and on join/quit/world-change. On wire every stored NPC is
        // shown to the online viewers in range; on disable the refresh timer is cancelled and every shown fake
        // player is removed from every viewer so a reload re-spawns cleanly with no ghost.
        NpcWiring.Wired wired = NpcWiring.wire(plugin, ctx, persistence, Optional.ofNullable(links.npcEconomy));
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
            ContextLinks links) {
        // discordlink builds its un-cached jOOQ store over persistence.dsl() (the discord_link_pending and
        // discord_links tables ship in the persistence V16 baseline, always applied) and the /discordlink and
        // /discordunlink commands. It registers its ConfirmLink seam into the ServicesManager so the optional
        // Discord bridge — a separate jar with no compile-time link to this one — can redeem a /link code through
        // the same use case; the registration is dropped on disable so a reload re-exposes it cleanly. The bridge
        // looks the service up once its gateway is ready and forwards nothing while it is absent.
        DiscordlinkWiring.Wired wired = DiscordlinkWiring.wire(ctx, persistence);
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
    }

    /** Cross-context handles captured during wiring so a dependent context reaches its prerequisite. */
    private static final class ContextLinks {
        private @org.jspecify.annotations.Nullable TeleportEngine teleportEngine;
        private @org.jspecify.annotations.Nullable WarpEconomy warpEconomy;
        private @org.jspecify.annotations.Nullable KitEconomy kitEconomy;
        private @org.jspecify.annotations.Nullable HomeEconomy homeEconomy;
        private com.uxplima.uxmessentials.vaults.application.port.@org.jspecify.annotations.Nullable VaultEconomy
                vaultEconomy;
        private @org.jspecify.annotations.Nullable NpcEconomy npcEconomy;
        private @org.jspecify.annotations.Nullable MutableMutePolicy mutePolicy;
        private @org.jspecify.annotations.Nullable MutableAfkStatus afkStatus;
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
