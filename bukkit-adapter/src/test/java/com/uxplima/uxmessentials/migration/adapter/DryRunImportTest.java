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
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.AccountNames;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.KitClaimTarget;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.UserRecordWriter;
import com.uxplima.uxmessentials.migration.convert.map.ImportedAccount;
import com.uxplima.uxmessentials.migration.convert.map.ImportedHologram;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWarp;
import com.uxplima.uxmessentials.migration.convert.map.ImportedWorld;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
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
 * A dry run reads the same server a live run would write to and reports, record for record, the outcome the live run
 * gets, without writing a single row. Each test seeds two identical servers, runs the dry-run writer over one (whose
 * stores refuse every write) and the live writer over the other, and compares.
 */
class DryRunImportTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-000000000001"), "world");
    private static final PlayerRef KNOWN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Known");
    private static final PlayerRef FRESH =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "Fresh");
    private static final Issuer CONSOLE = Issuer.console("LiteBans");
    private static final Warn OLD_WARNING = Warn.standing(CONSOLE, Optional.of("language"), NOW.minusSeconds(60));

    /** A pair of records of each kind the writers dispatch, the first colliding with what the server holds. */
    private static List<ImportRecord> records() {
        return List.of(
                new ImportRecord.AccountRecord(account(KNOWN)),
                new ImportRecord.AccountRecord(account(FRESH)),
                new ImportRecord.WarpRecord(new ImportedWarp(warp("market"))),
                new ImportRecord.WarpRecord(new ImportedWarp(warp("spawn"))),
                new ImportRecord.KitRecord(new ImportedKit(kit("starter"))),
                new ImportRecord.KitRecord(new ImportedKit(kit("tools"))),
                new ImportRecord.ModerationRecord(mute(KNOWN)),
                new ImportRecord.ModerationRecord(mute(FRESH)),
                new ImportRecord.BanRecord(KNOWN, ban()),
                new ImportRecord.BanRecord(FRESH, ban()),
                new ImportRecord.IpBanRecord(ipBan("10.0.0.5")),
                new ImportRecord.IpBanRecord(ipBan("10.0.0.6")),
                new ImportRecord.WarnRecord(KNOWN, OLD_WARNING),
                new ImportRecord.WarnRecord(KNOWN, Warn.standing(CONSOLE, Optional.of("spam"), NOW)),
                new ImportRecord.HologramRecord(new ImportedHologram(hologram("welcome"))),
                new ImportRecord.HologramRecord(new ImportedHologram(hologram("rules"))),
                new ImportRecord.WorldRecord(new ImportedWorld(world("resource"))),
                new ImportRecord.WorldRecord(new ImportedWorld(world("arena"))),
                new ImportRecord.UserRecord(
                        new ImportedUser(FRESH, List.of(), Optional.of(new BigDecimal("10")), List.of())),
                new ImportRecord.UserRecord(kitClaim(KNOWN)),
                new ImportRecord.UserRecord(kitClaim(FRESH)));
    }

    @Test
    void underSkipADryRunReportsEachOutcomeTheLiveRunGetsAndWritesNothing() {
        assertDryRunMatchesLive(ConflictPolicy.SKIP);
    }

    @Test
    void underOverwriteADryRunReportsEachOutcomeTheLiveRunGetsAndWritesNothing() {
        assertDryRunMatchesLive(ConflictPolicy.OVERWRITE);
    }

    @Test
    void underSkipEveryRecordTheServerAlreadyHoldsIsKeptAndTheRestWouldLand() {
        Server server = Server.seeded(true);
        ImportOptions options = options(ConflictPolicy.SKIP).asDryRun();

        List<RecordOutcome> outcomes = outcomes(server.dryRunWriter(), options);

        assertThat(outcomes)
                .containsExactly(
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.WRITTEN,
                        RecordOutcome.SKIPPED,
                        RecordOutcome.WRITTEN);
        assertThat(server.writes).isEmpty();
    }

    @Test
    void anImportedIpv6BanIsStoredInTheFormAPlayersAddressIsReadIn() {
        Server server = Server.seeded(false);

        server.liveWriter().write(new ImportRecord.IpBanRecord(ipBan("2001:DB8::7")), options(ConflictPolicy.SKIP));

        assertThat(server.writes).containsExactly("ip ban 2001:db8:0:0:0:0:0:7");
    }

    @Test
    void anImportedBanSpelledOtherwiseIsTheSameAddressBothRunsSkip() {
        Server live = Server.seeded(false);
        Server dryRun = Server.seeded(true);
        live.ipBans.put("2001:db8:0:0:0:0:0:7", ipBan("2001:db8:0:0:0:0:0:7"));
        dryRun.ipBans.put("2001:db8:0:0:0:0:0:7", ipBan("2001:db8:0:0:0:0:0:7"));
        ImportRecord compressed = new ImportRecord.IpBanRecord(ipBan("2001:DB8::7"));

        RecordOutcome stored = live.liveWriter()
                .write(compressed, options(ConflictPolicy.SKIP))
                .outcome();
        RecordOutcome predicted = dryRun.dryRunWriter()
                .write(compressed, options(ConflictPolicy.SKIP).asDryRun())
                .outcome();

        assertThat(stored).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(predicted).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(live.writes).isEmpty();
    }

    private static void assertDryRunMatchesLive(ConflictPolicy policy) {
        Server dryRunServer = Server.seeded(true);
        Server liveServer = Server.seeded(false);

        List<RecordOutcome> dryRun =
                outcomes(dryRunServer.dryRunWriter(), options(policy).asDryRun());
        List<RecordOutcome> live = outcomes(liveServer.liveWriter(), options(policy));

        assertThat(dryRun).isEqualTo(live);
        assertThat(dryRunServer.writes).isEmpty();
        assertThat(liveServer.writes).isNotEmpty();
    }

    private static List<RecordOutcome> outcomes(RecordWriter writer, ImportOptions options) {
        return records().stream()
                .map(rec -> writer.write(rec, options))
                .map(RecordResult::outcome)
                .toList();
    }

    private static ImportOptions options(ConflictPolicy policy) {
        return new ImportOptions(Path.of("source"), ImportMode.LIVE, policy, BalancePolicy.SKIP_IF_PRESENT);
    }

    private static ImportedAccount account(PlayerRef player) {
        return new ImportedAccount(player.uuid(), player.name(), NOW);
    }

    private static Warp warp(String name) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, 1, 64, 1), KNOWN, NOW);
    }

    private static KitDefinition kit(String id) {
        // No items: the live writer resolves each item through the server's item codec, which a unit test lacks.
        return KitDefinition.repeatable(KitId.of(id), List.of(), Duration.ZERO);
    }

    /** A player who claimed the one-time welcome kit in the source and brings nothing else. */
    private static ImportedUser kitClaim(PlayerRef player) {
        return new ImportedUser(
                player, List.of(), Optional.empty(), List.of(), Map.of(KitId.of("welcome"), NOW.minusSeconds(3600)));
    }

    private static ImportedModeration mute(PlayerRef target) {
        return new ImportedModeration(
                ModerationProfile.clean(target).withMute(MuteState.permanent(CONSOLE, Optional.of("spam"), NOW)),
                true,
                false);
    }

    private static TempbanState ban() {
        return TempbanState.active(NOW.plus(Duration.ofDays(7)), CONSOLE, Optional.of("griefing"), NOW);
    }

    private static IpBan ipBan(String ip) {
        return new IpBan(ip, Optional.empty(), Optional.of("alts"), Optional.empty(), CONSOLE, NOW);
    }

    private static Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 70, 0), List.of(HologramLine.of(name)), NOW);
    }

    private static ManagedWorld world(String name) {
        return ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), NOW);
    }

    /**
     * One server's stores. A server built for the dry run refuses every write, so a dry run that writes anything
     * fails the test where it happens; either way each write is recorded.
     */
    private static final class Server {

        private final boolean refuseWrites;
        private final List<String> writes = new ArrayList<>();
        private final Map<WarpName, Warp> warps = new LinkedHashMap<>();
        private final Map<KitId, KitDefinition> kits = new LinkedHashMap<>();
        private final Map<UUID, MuteState> mutes = new HashMap<>();
        private final Map<UUID, TempbanState> bans = new HashMap<>();
        private final Map<String, IpBan> ipBans = new HashMap<>();
        private final Map<UUID, List<Warn>> warns = new HashMap<>();
        private final Map<HologramName, Hologram> holograms = new LinkedHashMap<>();
        private final Map<WorldName, ManagedWorld> worlds = new LinkedHashMap<>();
        private final Map<UUID, Set<KitId>> kitClaims = new HashMap<>();
        private final Map<UUID, String> names = new HashMap<>();

        private Server(boolean refuseWrites) {
            this.refuseWrites = refuseWrites;
        }

        /** The state both runs start from: the first record of each pair already exists. */
        static Server seeded(boolean refuseWrites) {
            Server server = new Server(refuseWrites);
            server.warps.put(WarpName.of("market"), warp("market"));
            server.kits.put(KitId.of("starter"), kit("starter"));
            server.mutes.put(KNOWN.uuid(), MuteState.permanent(CONSOLE, Optional.empty(), NOW.minusSeconds(5)));
            server.bans.put(KNOWN.uuid(), ban());
            server.ipBans.put("10.0.0.5", ipBan("10.0.0.5"));
            server.warns.put(KNOWN.uuid(), new ArrayList<>(List.of(OLD_WARNING)));
            server.holograms.put(HologramName.of("welcome"), hologram("welcome"));
            server.worlds.put(WorldName.of("resource"), world("resource"));
            server.kitClaims.put(KNOWN.uuid(), new HashSet<>(Set.of(KitId.of("welcome"))));
            server.names.put(KNOWN.uuid(), KNOWN.name());
            return server;
        }

        RecordWriter dryRunWriter() {
            return new DryRunRecordWriter(
                    users(),
                    new Warps(),
                    new Moderation(),
                    new Kits(),
                    new Holograms(),
                    new Worlds(),
                    UntouchedImportPorts.playerWarpWriter(new UntouchedImportPorts.PlayerWarps()),
                    new Names(),
                    CLOCK);
        }

        RecordWriter liveWriter() {
            return new RepositoryRecordWriter(
                    users(),
                    new Warps(),
                    new Moderation(),
                    new Kits(),
                    new Holograms(),
                    new Worlds(),
                    UntouchedImportPorts.playerWarpWriter(new UntouchedImportPorts.PlayerWarps()),
                    new Names(),
                    CLOCK,
                    new QuietLog());
        }

        private UserRecordWriter users() {
            return new UserRecordWriter(
                    new UntouchedImportPorts.Homes(),
                    new Wallets(),
                    Optional.empty(),
                    new KitClaims(),
                    Duration.ZERO,
                    text -> text,
                    COINS,
                    CLOCK);
        }

        private void write(String what) {
            writes.add(what);
            if (refuseWrites) {
                throw new AssertionError("a dry run wrote " + what);
            }
        }

        private final class Names implements AccountNames {

            @Override
            public boolean record(ImportedAccount account) {
                if (names.containsKey(account.uuid())) {
                    return false;
                }
                write("account " + account.uuid());
                names.put(account.uuid(), account.name());
                return true;
            }

            @Override
            public boolean holds(ImportedAccount account) {
                return names.containsKey(account.uuid());
            }
        }

        private final class KitClaims implements KitClaimTarget {

            @Override
            public Set<KitId> claimed(PlayerRef player) {
                return Set.copyOf(kitClaims.getOrDefault(player.uuid(), Set.of()));
            }

            @Override
            public boolean record(PlayerRef player, KitId kit, Instant claimedAt) {
                write("kit claim " + player.uuid() + " " + kit);
                return kitClaims
                        .computeIfAbsent(player.uuid(), id -> new HashSet<>())
                        .add(kit);
            }
        }

        private final class Warps implements WarpRepository {

            @Override
            public Optional<Warp> find(WarpName name) {
                return Optional.ofNullable(warps.get(name));
            }

            @Override
            public List<Warp> all() {
                return List.copyOf(warps.values());
            }

            @Override
            public boolean exists(WarpName name) {
                return warps.containsKey(name);
            }

            @Override
            public void save(Warp warp) {
                write("warp " + warp.name());
                warps.put(warp.name(), warp);
            }

            @Override
            public void delete(WarpName name) {
                write("delete warp " + name);
            }

            @Override
            public void rate(WarpName name, UUID player, double rating) {
                write("rate warp " + name);
            }

            @Override
            public double averageRating(WarpName name) {
                return 0;
            }
        }

        private final class Kits implements KitRepository {

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
                write("kit " + definition.id());
                kits.put(definition.id(), definition);
            }

            @Override
            public void delete(KitId id) {
                write("delete kit " + id);
            }
        }

        private final class Holograms implements HologramRepository {

            @Override
            public Optional<Hologram> find(HologramName name) {
                return Optional.ofNullable(holograms.get(name));
            }

            @Override
            public List<Hologram> all() {
                return List.copyOf(holograms.values());
            }

            @Override
            public boolean exists(HologramName name) {
                return holograms.containsKey(name);
            }

            @Override
            public void save(Hologram hologram) {
                write("hologram " + hologram.name());
                holograms.put(hologram.name(), hologram);
            }

            @Override
            public void delete(HologramName name) {
                write("delete hologram " + name);
            }

            @Override
            public Set<UUID> manualViewers(HologramName name) {
                return Set.of();
            }

            @Override
            public void showTo(HologramName name, UUID viewer) {
                write("show hologram " + name);
            }

            @Override
            public void hideFrom(HologramName name, UUID viewer) {
                write("hide hologram " + name);
            }

            @Override
            public Set<UUID> blacklisted(HologramName name) {
                return Set.of();
            }

            @Override
            public void addToBlacklist(HologramName name, UUID viewer) {
                write("blacklist hologram " + name);
            }

            @Override
            public void removeFromBlacklist(HologramName name, UUID viewer) {
                write("unblacklist hologram " + name);
            }
        }

        private final class Worlds implements WorldRepository {

            @Override
            public Optional<ManagedWorld> find(WorldName name) {
                return Optional.ofNullable(worlds.get(name));
            }

            @Override
            public List<ManagedWorld> all() {
                return List.copyOf(worlds.values());
            }

            @Override
            public boolean exists(WorldName name) {
                return worlds.containsKey(name);
            }

            @Override
            public void save(ManagedWorld world) {
                write("world " + world.name());
                worlds.put(world.name(), world);
            }

            @Override
            public void delete(WorldName name) {
                write("delete world " + name);
            }

            @Override
            public List<NetworkWorld> network() {
                return worlds.values().stream()
                        .map(world -> new NetworkWorld(NetworkWorld.UNSCOPED, world))
                        .toList();
            }
        }

        private final class Moderation implements ModerationRepository {

            @Override
            public ModerationProfile load(PlayerRef target) {
                return ModerationProfile.clean(target).withMute(loadMute(target));
            }

            @Override
            public MuteState loadMute(PlayerRef target) {
                return mutes.getOrDefault(target.uuid(), MuteState.none());
            }

            @Override
            public JailState loadJail(PlayerRef target) {
                return JailState.none();
            }

            @Override
            public TempbanState loadTempban(PlayerRef target) {
                return bans.getOrDefault(target.uuid(), TempbanState.none());
            }

            @Override
            public List<BanEntry> activeBans(Instant now, int limit) {
                return List.of();
            }

            @Override
            public List<MuteEntry> activeMutes(Instant now, int limit) {
                return List.of();
            }

            @Override
            public List<JailEntry> activeJails(Instant now, int limit) {
                return List.of();
            }

            @Override
            public void saveMute(PlayerRef target, MuteState mute) {
                write("mute " + target.uuid());
                mutes.put(target.uuid(), mute);
            }

            @Override
            public void saveJail(PlayerRef target, JailState jail) {
                write("jail " + target.uuid());
            }

            @Override
            public void saveTempban(PlayerRef target, TempbanState tempban) {
                write("ban " + target.uuid());
                bans.put(target.uuid(), tempban);
            }

            @Override
            public boolean replaceMute(PlayerRef target, MuteState expected, MuteState replacement) {
                throw new UnsupportedOperationException("an import overwrites with save");
            }

            @Override
            public Optional<IpBan> loadIpBan(String ip) {
                throw new UnsupportedOperationException("an import overwrites with save");
            }

            @Override
            public boolean replaceIpBan(Optional<IpBan> expected, IpBan replacement) {
                throw new UnsupportedOperationException("an import overwrites with save");
            }

            @Override
            public boolean replaceTempban(PlayerRef target, TempbanState expected, TempbanState.Active replacement) {
                throw new UnsupportedOperationException("an import overwrites bans with saveTempban");
            }

            @Override
            public int appendWarn(PlayerRef target, Warn warn) {
                write("warn " + target.uuid());
                List<Warn> history = warns.computeIfAbsent(target.uuid(), ignored -> new ArrayList<>());
                history.add(warn);
                return history.size();
            }

            @Override
            public List<Warn> warns(PlayerRef target, Instant now) {
                return List.copyOf(warns.getOrDefault(target.uuid(), List.of()));
            }

            @Override
            public int clearWarns(PlayerRef target) {
                write("clear warns " + target.uuid());
                return 0;
            }

            @Override
            public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
                write("clear warns " + target.uuid());
                return 0;
            }

            @Override
            public void saveIpBan(IpBan ban) {
                write("ip ban " + ban.ip());
                ipBans.put(ban.ip(), ban);
            }

            @Override
            public boolean removeIpBan(String ip) {
                write("remove ip ban " + ip);
                return false;
            }

            @Override
            public Optional<IpBan> activeIpBan(String ip, Instant now) {
                return Optional.ofNullable(ipBans.get(ip))
                        .filter(ban -> ban.until().map(now::isBefore).orElse(true));
            }

            @Override
            public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
                write("seen " + who.uuid());
            }

            @Override
            public Optional<SeenRecord> seen(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public void ensureUserExists(PlayerRef target, Instant at) {
                write("user " + target.uuid());
            }

            @Override
            public boolean isLockedDown() {
                return false;
            }

            @Override
            public void setLockedDown(boolean enabled) {
                write("lockdown");
            }
        }

        private final class Wallets implements WalletRepository {

            @Override
            public Optional<Wallet> findByOwner(PlayerRef owner) {
                return Optional.empty();
            }

            @Override
            public Wallet ensureOwner(PlayerRef owner) {
                write("wallet " + owner.uuid());
                return Wallet.of(owner, Map.of());
            }

            @Override
            public void upsertBalance(PlayerRef owner, Money balance) {
                write("balance " + owner.uuid());
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

    /** The live writer's diagnostics, which these comparisons do not read. */
    private static final class QuietLog implements com.uxplima.uxmessentials.shared.application.port.Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
