package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.convert.map.ImportedHologram;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWarp;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWorld;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository;
import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldBlacklist;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * An import through the writers the migration wiring builds lands in the caches the running modules serve players
 * from, so an imported row is there without a restart and a later command on the same name sees it. Each module's
 * cache is the real decorator over a fake database, warmed as the module warms it on enable. The fallback stores
 * are separate caches over the same fake databases: a write through one of them reaches the database but not the
 * module's warmed cache, which is why the module's own cache has to be the one written through.
 */
class LiveImportTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-000000000001"), "world");
    private static final PlayerRef FOUNDER =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Founder");
    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "Staff");
    private static final ImportOptions SKIP =
            new ImportOptions(Path.of("source"), ImportMode.LIVE, ConflictPolicy.SKIP, BalancePolicy.SKIP_IF_PRESENT);
    private static final ImportOptions OVERWRITE =
            new ImportOptions(Path.of("source"), ImportMode.LIVE, ConflictPolicy.OVERWRITE, BalancePolicy.OVERWRITE);

    private final RecordingLogger log = new RecordingLogger();
    private final ImportTargets targets = new ImportTargets(log);
    private final Databases databases = new Databases();
    private final RecordingBus bus = new RecordingBus();
    private final DeferringScheduler scheduler = new DeferringScheduler();
    private WorldBlacklist warpWorlds = WorldBlacklist.none();

    private MigrationWiring.Writers writers() {
        return MigrationWiring.writers(
                targets,
                databases,
                new MigrationWiring.WriterPorts(
                        new UntouchedImportPorts.Moderation(),
                        UntouchedImportPorts::playerWarpWriter,
                        Optional.empty(),
                        Duration.ZERO,
                        new UntouchedImportPorts.KitClaims(),
                        COINS,
                        () -> warpWorlds,
                        WorldBlacklist::none,
                        CLOCK,
                        log),
                bus,
                scheduler);
    }

    private CachedWarpRepository liveWarps() {
        CachedWarpRepository live = new CachedWarpRepository(databases.warps);
        live.all();
        targets.warps(live);
        return live;
    }

    private static Warp warp(String name, PlayerRef owner, double x) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, x, 64, x), owner, NOW);
    }

    private static Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 70, 0), List.of(HologramLine.of(name)), NOW);
    }

    private static ImportRecord warpRecord(Warp warp) {
        return new ImportRecord.WarpRecord(new ImportedWarp(warp));
    }

    private static ImportRecord hologramRecord(Hologram hologram) {
        return new ImportRecord.HologramRecord(new ImportedHologram(hologram));
    }

    @Test
    void underSkipAKitKeptOpenToEveryPlayerIsNamedToTheOperator() {
        MemoryKits live = new MemoryKits();
        live.save(KitDefinition.repeatable(KitId.of("vip"), List.of(), Duration.ZERO));
        live.save(KitDefinition.repeatable(KitId.of("starter"), List.of(), Duration.ZERO)
                .withPermission(true));
        targets.kits(live);
        ImportedKit vip = new ImportedKit(KitDefinition.repeatable(KitId.of("vip"), List.of(), Duration.ZERO)
                .withPermission(true));
        ImportedKit starter = new ImportedKit(KitDefinition.repeatable(KitId.of("starter"), List.of(), Duration.ZERO)
                .withPermission(true));

        RecordOutcome outcome =
                writers().live().write(new ImportRecord.KitRecord(vip), SKIP).outcome();
        writers().live().write(new ImportRecord.KitRecord(starter), SKIP);

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(live.find(KitId.of("vip")).orElseThrow().requiresPermission())
                .isFalse();
        assertThat(log.warnings).hasSize(1).first().asString().contains("vip", "permission = true");
    }

    @Test
    void anImportedWarpIsServedByTheRunningWarpsModuleWithoutARestart() {
        CachedWarpRepository live = liveWarps();
        Warp imported = warp("market", FOUNDER, 10);

        RecordOutcome outcome =
                writers().live().write(warpRecord(imported), SKIP).outcome();

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(live.find(WarpName.of("market"))).contains(imported);
    }

    @Test
    void aWarpInAWorldTheWarpsModuleBarsIsSkippedByTheLiveAndTheDryRunWriter() {
        CachedWarpRepository live = liveWarps();
        warpWorlds = WorldBlacklist.of(List.of(WORLD.name()));
        ImportRecord barred = warpRecord(warp("market", FOUNDER, 10));
        AnnouncingRecordWriter writer = writers().live();

        RecordResult fromLive = writer.write(barred, OVERWRITE);
        RecordResult fromDryRun = writers().dryRun().write(barred, OVERWRITE);
        writer.announce();

        assertThat(fromLive.outcome()).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(fromLive.notes()).contains("held_back=world_blacklist");
        assertThat(fromDryRun).isEqualTo(fromLive);
        assertThat(live.find(WarpName.of("market"))).isEmpty();
        assertThat(bus.published).as("nothing changed, so nothing is announced").isEmpty();
    }

    @Test
    void underSkipAWarpTheModuleAlreadyHoldsIsKept() {
        CachedWarpRepository live = liveWarps();
        Warp existing = warp("market", STAFF, 1);
        live.save(existing);

        RecordOutcome outcome = writers()
                .live()
                .write(warpRecord(warp("market", FOUNDER, 10)), SKIP)
                .outcome();

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(live.find(WarpName.of("market"))).contains(existing);
    }

    @Test
    void underOverwriteTheImportedWarpReplacesTheOneTheModuleServes() {
        CachedWarpRepository live = liveWarps();
        live.save(warp("market", STAFF, 1));
        Warp imported = warp("market", FOUNDER, 10);

        RecordOutcome outcome =
                writers().live().write(warpRecord(imported), OVERWRITE).outcome();

        assertThat(outcome).isEqualTo(RecordOutcome.OVERWRITTEN);
        assertThat(live.find(WarpName.of("market"))).contains(imported);
    }

    @Test
    void setwarpOnAnImportedNameMovesTheImportedWarpInsteadOfCreatingOneOverIt() {
        CachedWarpRepository live = liveWarps();
        writers().live().write(warpRecord(warp("market", FOUNDER, 10)), SKIP);
        RecordingEvents events = new RecordingEvents();
        SetWarp setWarp = new SetWarp(
                live,
                new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {}),
                events,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC),
                WorldBlacklist.none(),
                Optional.of("lobby"));
        Position moved = Position.of(WORLD, 50, 70, 50);

        setWarp.set(STAFF, WarpName.of("market"), moved);

        Warp after = live.find(WarpName.of("market")).orElseThrow();
        assertThat(after.location()).isEqualTo(moved);
        assertThat(after.owner()).isEqualTo(FOUNDER);
        assertThat(after.createdAt()).isEqualTo(NOW);
        assertThat(events.published)
                .as("an existing warp is moved, not created")
                .isEmpty();
    }

    @Test
    void anImportedHologramIsServedAndDrawnByTheRunningHologramsModuleWithoutARestart() {
        CachedHologramRepository live = new CachedHologramRepository(databases.holograms);
        live.all();
        RecordingView view = new RecordingView();
        targets.holograms(live, view);
        Hologram imported = hologram("welcome");

        writers().live().write(hologramRecord(imported), SKIP);

        assertThat(live.find(HologramName.of("welcome"))).contains(imported);
        assertThat(view.rendered).containsExactly(imported);
    }

    @Test
    void aHologramThatCannotBeDrawnIsStillStoredAndReported() {
        CachedHologramRepository live = new CachedHologramRepository(databases.holograms);
        live.all();
        HologramView broken = new RecordingView() {
            @Override
            public void render(Hologram hologram) {
                throw new IllegalStateException("no such world");
            }
        };
        targets.holograms(live, broken);

        RecordOutcome outcome = writers()
                .live()
                .write(hologramRecord(hologram("welcome")), SKIP)
                .outcome();

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(live.exists(HologramName.of("welcome"))).isTrue();
        assertThat(log.warnings).anySatisfy(line -> assertThat(line).contains("welcome"));
    }

    @Test
    void anImportedWorldIsListedByTheRunningWorldsModuleWithoutARestart() {
        CachedWorldRepository live = new CachedWorldRepository(databases.worlds, WorldDatabase.SERVER);
        live.all();
        targets.worlds(live);
        ManagedWorld imported =
                ManagedWorld.created(WorldName.of("resource"), WorldSpec.normal(), true, Optional.empty(), NOW);

        writers().live().write(new ImportRecord.WorldRecord(new ImportedWorld(imported)), SKIP);

        assertThat(live.all()).containsExactly(imported);
    }

    @Test
    void anImportedBalanceIsWhatTheRunningEconomyModuleReads() {
        databases.wallets.upsertBalance(FOUNDER, Money.of(COINS, 5));
        CachedWalletRepository live = new CachedWalletRepository(databases.wallets);
        live.findByOwner(FOUNDER);
        targets.wallets(live);
        ImportedUser user = new ImportedUser(FOUNDER, List.of(), Optional.of(new BigDecimal("100")), List.of());

        writers().live().write(new ImportRecord.UserRecord(user), OVERWRITE);

        assertThat(live.findByOwner(FOUNDER).orElseThrow().balanceOf(COINS).amount())
                .isEqualByComparingTo("100");
    }

    @Test
    void aModuleThatIsNotRunningFallsBackToTheStoreOverTheDatabase() {
        Warp imported = warp("market", FOUNDER, 10);

        writers().live().write(warpRecord(imported), SKIP);

        assertThat(databases.warpStoresBuilt).isEqualTo(1);
        assertThat(databases.warps.find(WarpName.of("market"))).contains(imported);
    }

    @Test
    void aModuleThatWithdrewItsRegistrationIsNotWrittenThrough() {
        CachedWarpRepository live = new CachedWarpRepository(databases.warps);
        live.all();
        targets.warps(live).run();

        writers().live().write(warpRecord(warp("market", FOUNDER, 10)), SKIP);

        assertThat(databases.warpStoresBuilt).isEqualTo(1);
    }

    @Test
    void peersHearOneWarpFrameAndEachChangedHologramOnceInSpacedBatches() {
        liveWarps();
        targets.holograms(new CachedHologramRepository(databases.holograms), new RecordingView());
        AnnouncingRecordWriter writer = writers().live();
        writer.write(warpRecord(warp("market", FOUNDER, 10)), OVERWRITE);
        writer.write(warpRecord(warp("spawn", FOUNDER, 20)), OVERWRITE);
        int holograms = AnnouncingRecordWriter.HOLOGRAM_BATCH * 2 + 3;
        for (int index = 0; index < holograms; index++) {
            writer.write(hologramRecord(hologram("board-" + index)), OVERWRITE);
        }
        writer.write(hologramRecord(hologram("board-0")), OVERWRITE);

        assertThat(bus.published).as("nothing is sent while the import runs").isEmpty();
        writer.announce();

        assertThat(bus.published).filteredOn(WarpChanged.class::isInstance).hasSize(1);
        assertThat(bus.published)
                .filteredOn(HologramChanged.class::isInstance)
                .hasSize(AnnouncingRecordWriter.HOLOGRAM_BATCH);
        while (scheduler.runNext()) {
            assertThat(scheduler.lastDelay).isEqualTo(AnnouncingRecordWriter.BATCH_GAP);
        }
        assertThat(bus.published)
                .filteredOn(HologramChanged.class::isInstance)
                .extracting(message -> ((HologramChanged) message).name())
                .hasSize(holograms)
                .doesNotHaveDuplicates();
    }

    @Test
    void aSkippedRecordIsNotAnnounced() {
        CachedWarpRepository live = liveWarps();
        live.save(warp("market", STAFF, 1));
        AnnouncingRecordWriter writer = writers().live();

        writer.write(warpRecord(warp("market", FOUNDER, 10)), SKIP);
        writer.announce();

        assertThat(bus.published).isEmpty();
    }

    /** The fake databases, and the fallback stores the wiring builds over them for a module that is off. */
    private static final class Databases implements MigrationWiring.FallbackStores {

        private final WarpDatabase warps = new WarpDatabase();
        private final HologramDatabase holograms = new HologramDatabase();
        private final WorldDatabase worlds = new WorldDatabase();
        private final WalletDatabase wallets = new WalletDatabase();
        private int warpStoresBuilt;

        @Override
        public HomeRepository homes() {
            return new UntouchedImportPorts.Homes();
        }

        @Override
        public WalletRepository wallets() {
            return new CachedWalletRepository(wallets);
        }

        @Override
        public WarpRepository warps() {
            warpStoresBuilt++;
            return new CachedWarpRepository(warps);
        }

        @Override
        public KitRepository kits() {
            return new UntouchedImportPorts.Kits();
        }

        @Override
        public HologramRepository holograms() {
            return new CachedHologramRepository(holograms);
        }

        @Override
        public WorldRepository worlds() {
            return new CachedWorldRepository(worlds, WorldDatabase.SERVER);
        }

        @Override
        public PlayerWarpRepository playerWarps() {
            return new UntouchedImportPorts.PlayerWarps();
        }
    }

    private static final class RecordingBus implements BusPublisher {

        private final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "lobby";
        }
    }

    /** Holds each delayed task until the test runs it, so the spacing between batches is visible. */
    private static final class DeferringScheduler implements Scheduler {

        private final List<Runnable> delayed = new ArrayList<>();
        private Duration lastDelay = Duration.ZERO;
        private final List<Duration> delays = new ArrayList<>();

        boolean runNext() {
            if (delayed.isEmpty()) {
                return false;
            }
            lastDelay = delays.remove(0);
            delayed.remove(0).run();
            return true;
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
            delayed.add(task);
        }
    }

    /** The kits module's catalog in memory. */
    private static final class MemoryKits implements KitRepository {

        private final Map<KitId, KitDefinition> kits = new LinkedHashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(kits.get(id));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(kits.values());
        }

        @Override
        public boolean exists(KitId id) {
            return kits.containsKey(id);
        }

        @Override
        public void save(KitDefinition definition) {
            kits.put(definition.id(), definition);
        }

        @Override
        public void delete(KitId id) {
            kits.remove(id);
        }
    }

    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String line = message;
            for (Object arg : args) {
                line = line.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
            }
            warnings.add(line);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class RecordingEvents implements DomainEventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static class RecordingView implements HologramView {

        private final List<Hologram> rendered = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram);
        }

        @Override
        public void despawn(HologramName name) {}

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }

    /** The warps table. */
    private static final class WarpDatabase implements WarpRepository {

        private final Map<String, Warp> rows = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            rows.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            rows.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0;
        }
    }

    /** The holograms table, with its viewer and blacklist rows. */
    private static final class HologramDatabase implements HologramRepository {

        private final Map<String, Hologram> rows = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> viewers = new HashMap<>();
        private final Map<String, Set<UUID>> blacklist = new HashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            rows.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            rows.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return Set.copyOf(viewers.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            viewers.computeIfAbsent(name.value(), ignored -> new HashSet<>()).add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            viewers.getOrDefault(name.value(), new HashSet<>()).remove(viewer);
        }

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.copyOf(blacklist.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            blacklist.computeIfAbsent(name.value(), ignored -> new HashSet<>()).add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            blacklist.getOrDefault(name.value(), new HashSet<>()).remove(viewer);
        }
    }

    /** The managed worlds table. */
    private static final class WorldDatabase implements WorldRepository {

        static final String SERVER = "lobby";

        private final Map<WorldName, ManagedWorld> rows = new LinkedHashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(rows.get(name));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return rows.containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            rows.put(world.name(), world);
        }

        @Override
        public void delete(WorldName name) {
            rows.remove(name);
        }

        @Override
        public List<NetworkWorld> network() {
            return rows.values().stream()
                    .map(world -> new NetworkWorld(SERVER, world))
                    .toList();
        }
    }

    /** The wallets table. The import only reads and sets balances. */
    private static final class WalletDatabase implements WalletRepository {

        private final Map<UUID, Map<Currency, Money>> balances = new HashMap<>();

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            Map<Currency, Money> owned = balances.get(owner.uuid());
            return owned == null ? Optional.empty() : Optional.of(Wallet.of(owner, Map.copyOf(owned)));
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return Wallet.of(owner, Map.copyOf(balances.computeIfAbsent(owner.uuid(), ignored -> new HashMap<>())));
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            balances.computeIfAbsent(owner.uuid(), ignored -> new HashMap<>()).put(balance.currency(), balance);
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw UntouchedImportPorts.touched();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw UntouchedImportPorts.touched();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw UntouchedImportPorts.touched();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            throw UntouchedImportPorts.touched();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            throw UntouchedImportPorts.touched();
        }
    }
}
