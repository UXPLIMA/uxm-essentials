package com.uxplima.uxmessentials.migration.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.EconomyConfig;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.messaging.adapter.MessagingSettings;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.migration.BackupSnapshot;
import com.uxplima.uxmessentials.migration.ImportData;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.MigrationAudit;
import com.uxplima.uxmessentials.migration.MigrationModule;
import com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.UserRecordWriter;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.SourceRegistry;
import com.uxplima.uxmessentials.migration.convert.athelion.AthelionPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.ax.AxPlayerWarpsConvert;
import com.uxplima.uxmessentials.migration.convert.decentholograms.DecentHologramsConvert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXConvert;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.migration.convert.fancyholograms.FancyHologramsConvert;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConfig;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConvert;
import com.uxplima.uxmessentials.migration.convert.live.LiveBalanceConvert;
import com.uxplima.uxmessentials.migration.convert.multiverse.MultiverseConvert;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConfig;
import com.uxplima.uxmessentials.migration.convert.olzie.OlziePlayerWarpsConvert;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.persistence.economy.WalletRepositories;
import com.uxplima.uxmessentials.persistence.holograms.HologramRepositories;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.messaging.MessagingStores;
import com.uxplima.uxmessentials.persistence.moderation.ModerationStores;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.warps.WarpRepositories;
import com.uxplima.uxmessentials.persistence.worlds.WorldRepositories;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.AuditChannel;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import org.jspecify.annotations.NullMarked;

/**
 * The one place the migration adapter is wired. It assembles the EssentialsX
 * {@link Convert} source, the source registry, the audit/backup ports, the live and dry-run writers over
 * the persistence repositories, the {@link ImportData} use case, and the inbound
 * {@link MigrationImportService} the {@code /uxmess import} node calls. Nothing it builds runs at plugin
 * enable, the importer fires only on the command, and the whole wiring is gated by the
 * {@link MigrationModule#enabled(ConfigStore)} switch, which ships disabled (§1, §9). A disabled module
 * yields a service that reports the importer dormant and dispatches nothing.
 */
@NullMarked
public final class MigrationWiring {

    private static final String DEFAULT_SOURCE_PATH = "Essentials";

    private MigrationWiring() {}

    /**
     * Build the migration service.
     *
     * @param plugin the plugin (for the server world list, data folder, and plugins directory)
     * @param persistence the shared persistence DSL the writers upsert through
     * @param migrationConfig the {@code modules.migration} config subtree (source path, conflict and balance policies)
     * @param economyConfig the {@code modules.economy} config subtree (the default currency)
     * @param scheduler the off-tick dispatch port
     * @param log operator diagnostics
     * @param enabled the {@link MigrationModule} enable gate (ships disabled)
     * @param messagingConfig the {@code modules.messaging} config subtree (the mail retention), empty when the
     *     messaging module is off; then imported mail is skipped and audited
     * @param targets the running modules' own repositories, which the import writes through so their caches see it
     * @param publisher the cross-server bus, which hears what an import changed once it ends
     * @param worldScope the server imported worlds are recorded under when the worlds module is not running, the
     *     same scope that module keeps this backend's worlds under
     */
    public static MigrationImportService wire(
            Plugin plugin,
            Persistence persistence,
            ConfigStore migrationConfig,
            ConfigStore economyConfig,
            Scheduler scheduler,
            Logger log,
            AuditChannel auditChannel,
            boolean enabled,
            Optional<ConfigStore> messagingConfig,
            ImportTargets targets,
            BusPublisher publisher,
            String worldScope) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(migrationConfig, "migrationConfig");
        Objects.requireNonNull(economyConfig, "economyConfig");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(publisher, "publisher");
        Objects.requireNonNull(worldScope, "worldScope");
        SourceRegistry registry = sourceRegistry(plugin, migrationConfig, economyConfig, log);
        ImportData importData = new ImportData(audit(auditChannel), backup(plugin, log), log);
        Writers writers = writers(
                targets,
                new DatabaseStores(plugin, persistence, economyConfig, log, worldScope),
                ports(persistence, economyConfig, log, messagingConfig),
                publisher,
                scheduler);
        ImportOptions options = options(plugin, migrationConfig, log);
        return new MigrationImportService(
                registry, importData, writers.live(), writers.dryRun(), options, scheduler, log, enabled);
    }

    private static SourceRegistry sourceRegistry(
            Plugin plugin, ConfigStore migrationConfig, ConfigStore economyConfig, Logger log) {
        WorldNameResolver worlds = new BukkitWorldNameResolver(plugin.getServer());
        Currency defaultCurrency = new EconomyConfig(economyConfig).currencies().defaultCurrency();
        List<Convert> built = new ArrayList<>();
        built.add(new EssentialsXConvert(worlds, log));
        built.add(new LiveBalanceConvert(
                SourceId.of("vault"),
                "Vault",
                "the live Vault economy provider",
                new VaultBalanceFeed(plugin, defaultCurrency)));
        built.add(new LiveBalanceConvert(
                SourceId.of("playerpoints"),
                "PlayerPoints",
                "the live PlayerPoints plugin",
                new PlayerPointsBalanceFeed(plugin, log)));
        built.add(new LiteBansConvert(liteBansConfig(plugin, migrationConfig), log));
        built.add(new DecentHologramsConvert(worlds, decentHologramsDirectory(plugin)));
        built.add(new FancyHologramsConvert(worlds, fancyHologramsFile(plugin)));
        built.add(new AxPlayerWarpsConvert(axPlayerWarpsConfig(plugin, migrationConfig), worlds, log));
        built.add(new AthelionPlayerWarpsConvert(worlds, athelionDataFile(plugin, migrationConfig), log));
        built.add(new OlziePlayerWarpsConvert(olziePlayerWarpsConfig(plugin, migrationConfig), worlds, log));
        built.add(new MultiverseConvert(multiverseWorldsFile(plugin), Clock.systemUTC(), log));
        return new SourceRegistry(built);
    }

    /** Read the {@code modules.migration.olzieplayerwarps} subtree into the source's connection config. */
    private static OlziePlayerWarpsConfig olziePlayerWarpsConfig(Plugin plugin, ConfigStore migrationConfig) {
        ConfigStore olzie = migrationConfig.scoped("olzieplayerwarps");
        String jdbcUrl = olzie.getString("jdbc-url", "");
        String username = olzie.getString("username", "");
        String password = olzie.getString("password", "");
        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        return new OlziePlayerWarpsConfig(Optional.of(jdbcUrl), username, password, Optional.ofNullable(pluginsDir));
    }

    /** Read the {@code modules.migration.axplayerwarps} subtree into the source's connection config. */
    private static AxPlayerWarpsConfig axPlayerWarpsConfig(Plugin plugin, ConfigStore migrationConfig) {
        ConfigStore ax = migrationConfig.scoped("axplayerwarps");
        String jdbcUrl = ax.getString("jdbc-url", "");
        String username = ax.getString("username", "");
        String password = ax.getString("password", "");
        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        return new AxPlayerWarpsConfig(Optional.of(jdbcUrl), username, password, Optional.ofNullable(pluginsDir));
    }

    /**
     * The Athelion {@code data.yml} the source reads, from the {@code modules.migration.athelionplayerwarps.data-file}
     * override or the default {@code plugins/PlayerWarps/data.yml} Athelion writes.
     */
    private static Path athelionDataFile(Plugin plugin, ConfigStore migrationConfig) {
        String configured =
                migrationConfig.scoped("athelionplayerwarps").getString("data-file", "PlayerWarps/data.yml");
        return pluginsDirectory(plugin).resolve(configured);
    }

    /** The {@code plugins/DecentHolograms/holograms} directory the DecentHolograms source reads its files from. */
    private static Path decentHologramsDirectory(Plugin plugin) {
        return pluginsDirectory(plugin).resolve("DecentHolograms").resolve("holograms");
    }

    /** The {@code plugins/Multiverse-Core/worlds.yml} file the Multiverse source reads the world registry from. */
    private static Path multiverseWorldsFile(Plugin plugin) {
        return pluginsDirectory(plugin).resolve("Multiverse-Core").resolve("worlds.yml");
    }

    /** The {@code plugins/FancyHolograms/holograms.yml} file the FancyHolograms source reads its holograms from. */
    private static Path fancyHologramsFile(Plugin plugin) {
        return pluginsDirectory(plugin).resolve("FancyHolograms").resolve("holograms.yml");
    }

    /** The server {@code plugins/} directory, falling back to a relative path when the data folder has no parent. */
    private static Path pluginsDirectory(Plugin plugin) {
        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        return pluginsDir != null ? pluginsDir : Path.of("plugins");
    }

    /** Read the {@code modules.migration.litebans} subtree into the source's connection config. */
    private static LiteBansConfig liteBansConfig(Plugin plugin, ConfigStore migrationConfig) {
        ConfigStore litebans = migrationConfig.scoped("litebans");
        String jdbcUrl = litebans.getString("jdbc-url", "");
        String username = litebans.getString("username", "");
        String password = litebans.getString("password", "");
        String tablePrefix = litebans.getString("table-prefix", "litebans_");
        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        return new LiteBansConfig(
                Optional.of(jdbcUrl), username, password, tablePrefix, Optional.ofNullable(pluginsDir));
    }

    /**
     * The live and dry-run writers. Each module target is the running module's own cache, taken from
     * {@code targets}; only a module that is off gets a store from {@code stores}. The live writer tells peers what
     * changed once an import ends.
     */
    static Writers writers(
            ImportTargets targets,
            FallbackStores stores,
            WriterPorts ports,
            BusPublisher publisher,
            Scheduler scheduler) {
        // The conflict checks read each imported owner's homes and wallet through these caches, so a large import
        // passes every owner through them. The home and wallet caches are bounded and expire, so that churns the
        // cache for a while but cannot grow it, and the port has no read that skips the cache to use instead.
        HomeRepository homes = targets.homesOr(stores::homes);
        WalletRepository wallets = targets.walletsOr(stores::wallets);
        WarpRepository warps = targets.warpsOr(stores::warps);
        KitRepository kits = targets.kitsOr(stores::kits);
        HologramRepository holograms = targets.hologramsOr(stores::holograms);
        WorldRepository worldRegistry = targets.worldsOr(stores::worlds);
        PlayerWarpRecordWriter playerWarps = ports.playerWarps().apply(targets.playerWarpsOr(stores::playerWarps));
        UserRecordWriter users = new UserRecordWriter(
                homes,
                wallets,
                ports.mail(),
                ports.mailRetention(),
                StyledText::escape,
                ports.defaultCurrency(),
                ports.clock());
        RecordWriter repositories = new RepositoryRecordWriter(
                users,
                warps,
                ports.moderation(),
                kits,
                holograms,
                worldRegistry,
                playerWarps,
                ports.clock(),
                ports.log());
        RecordWriter dryRun = new DryRunRecordWriter(
                users, warps, ports.moderation(), kits, holograms, worldRegistry, playerWarps, ports.clock());
        return new Writers(new AnnouncingRecordWriter(repositories, publisher, scheduler), dryRun);
    }

    private static WriterPorts ports(
            Persistence persistence, ConfigStore economyConfig, Logger log, Optional<ConfigStore> messagingConfig) {
        Clock clock = Clock.systemUTC();
        // The mail table ships with every install, but a disabled messaging module means the operator does not want
        // mail, so the import leaves it out rather than filling a mailbox nobody can open.
        Optional<MailRepository> mail = messagingConfig.map(ignored -> MessagingStores.mail(persistence));
        Duration mailRetention = messagingConfig
                .map(config -> new MessagingSettings(config).mailRetention())
                .orElse(Duration.ZERO);
        // Moderation, mail and the player-warp side stores keep no cache: every reader queries the database, so a
        // fresh store sees exactly what the running modules see.
        return new WriterPorts(
                ModerationStores.repository(persistence),
                repository -> playerWarpWriter(repository, persistence, clock, log),
                mail,
                mailRetention,
                new EconomyConfig(economyConfig).currencies().defaultCurrency(),
                clock,
                log);
    }

    /**
     * The shared player-warp import writer, built over the player-warps ports the P4 module owns, the same
     * repository, password store, and social stores {@code /setpwarp} and the browse GUI use, so an imported warp
     * can never reach a state a live command could not. The AxPlayerWarps, Athelion and Olzie sources all funnel
     * through this one writer.
     */
    private static PlayerWarpRecordWriter playerWarpWriter(
            PlayerWarpRepository repository, Persistence persistence, Clock clock, Logger log) {
        // The server's profile cache names the owner of a warp whose source kept only the uuid.
        PlayerLookup players = new BukkitPlayerLookup();
        return new PlayerWarpRecordWriter(
                repository,
                PlayerWarpRepositories.passwordStore(persistence),
                PlayerWarpRepositories.memberStore(persistence, log),
                PlayerWarpRepositories.whitelistStore(persistence, clock),
                PlayerWarpRepositories.banStore(persistence),
                PlayerWarpRepositories.favouriteStore(persistence, clock),
                PlayerWarpRepositories.ratingStore(persistence),
                uuid -> players.findByUuid(uuid).map(PlayerRef::name));
    }

    /**
     * The file-backed kit catalog, the same {@code modules/kits/kits/} tree the kits module owns, since kits are
     * deliberately file-based rather than in the relational store. Used only when the kits module is off; a running
     * kits module hands its own catalog in, so an imported kit is offered by {@code /kit} at once.
     */
    private static KitRepository kitRepository(Plugin plugin, Logger log) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path kitsDir = dataFolder.resolve("modules").resolve("kits").resolve("kits");
        Path legacy = dataFolder.resolve("kits.conf");
        return ConfigurateKitRepository.load(kitsDir, legacy, log);
    }

    private static MigrationAudit audit(AuditChannel auditChannel) {
        return new AuditChannelMigrationAudit(auditChannel.logger());
    }

    private static BackupSnapshot backup(Plugin plugin, Logger log) {
        return new DataDirBackupSnapshot(plugin.getDataFolder().toPath(), log);
    }

    private static ImportOptions options(Plugin plugin, ConfigStore config, Logger log) {
        Path pluginsDir = plugin.getDataFolder().toPath().getParent();
        String configured = config.getString("source-path", DEFAULT_SOURCE_PATH);
        Path sourcePath = pluginsDir == null ? Path.of(configured) : pluginsDir.resolve(configured);
        ImportPolicies policies = ImportPolicies.read(config, log);
        return new ImportOptions(sourcePath, ImportMode.LIVE, policies.onConflict(), policies.balancePolicy());
    }

    /**
     * The stores an import falls back to for a module that is off: plain caches over the database, and the kit
     * files for kits. None carries a cross-server broadcaster, since the live writer announces once per import
     * rather than once per row. A seam so a test can stand in stores over a fake database.
     */
    interface FallbackStores {

        HomeRepository homes();

        WalletRepository wallets();

        WarpRepository warps();

        KitRepository kits();

        HologramRepository holograms();

        WorldRepository worlds();

        PlayerWarpRepository playerWarps();
    }

    /** The stores over the shared database. Each is built only for a module that is off. */
    private static final class DatabaseStores implements FallbackStores {

        private final Plugin plugin;
        private final Persistence persistence;
        private final ConfigStore economyConfig;
        private final Logger log;
        private final String server;

        DatabaseStores(Plugin plugin, Persistence persistence, ConfigStore economyConfig, Logger log, String server) {
            this.plugin = plugin;
            this.server = server;
            this.persistence = persistence;
            this.economyConfig = economyConfig;
            this.log = log;
        }

        @Override
        public HomeRepository homes() {
            return HomeRepositories.cached(persistence);
        }

        @Override
        public WalletRepository wallets() {
            CurrencyRegistry currencies = new EconomyConfig(economyConfig).currencies();
            return WalletRepositories.repository(persistence, currencies, Clock.systemUTC());
        }

        @Override
        public WarpRepository warps() {
            return WarpRepositories.cached(persistence);
        }

        @Override
        public KitRepository kits() {
            return kitRepository(plugin, log);
        }

        @Override
        public HologramRepository holograms() {
            return HologramRepositories.cached(persistence);
        }

        @Override
        public WorldRepository worlds() {
            return WorldRepositories.cached(persistence, server);
        }

        @Override
        public PlayerWarpRepository playerWarps() {
            return PlayerWarpRepositories.cached(persistence);
        }
    }

    /** What the writers need besides the module targets: the uncached stores and the import settings. */
    record WriterPorts(
            ModerationRepository moderation,
            Function<PlayerWarpRepository, PlayerWarpRecordWriter> playerWarps,
            Optional<MailRepository> mail,
            Duration mailRetention,
            Currency defaultCurrency,
            Clock clock,
            Logger log) {}

    /** The live writer, which announces what an import changed, and the dry-run writer. */
    record Writers(AnnouncingRecordWriter live, RecordWriter dryRun) {}
}
