package com.uxplima.uxmessentials.guard.adapter.outbound;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand.ImportKind;
import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardCommand.ImportOutcome;
import com.uxplima.uxmessentials.guard.application.EditContext;
import com.uxplima.uxmessentials.guard.application.GuardService;
import com.uxplima.uxmessentials.guard.application.port.GuardBackups;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Imports WorldGuard regions (G01) from its YAML files or its SQL store. A dry run writes the report and changes
 * nothing; a run takes a backup, stores the converted regions tagged with a batch id, and remembers the batch; an
 * undo removes the last batch.
 *
 * <p>WorldGuard lets only members build in a region without flags. That default becomes explicit rules here
 * (everyone denied, owners, members and member groups allowed) because a guard region without rules does nothing.
 * Every flag either becomes rules or behaviours or is listed in the report as not converted.
 *
 * <p>The files, the database and the report are read and written on the async lane. Players stored by name are
 * looked up on the global thread, the one place the server's player cache may be asked. Only one import runs at a
 * time: a second request while one is running is answered with {@link ImportKind#BUSY} and starts nothing, since two
 * runs would race on the batch file and the second backup would already hold the first run's regions.
 */
public final class WorldGuardImporter {

    /** The build family WorldGuard's membership default covers. */
    private static final List<String> BUILD = List.of(
            "break",
            "place",
            "use",
            "interact",
            "damage.animal",
            "damage.villager",
            "damage.armor-stand",
            "damage.hanging",
            "damage.vehicle",
            "vehicle.place",
            "vehicle.destroy",
            "hanging.place",
            "hanging.break",
            "item.use.bucket",
            "item.use.lighter",
            "item.use.bone-meal",
            "item.use.spawn-egg",
            "trample");

    /** WorldGuard state flags and the actions each covers. */
    private static final Map<String, List<String>> STATE = Map.ofEntries(
            Map.entry("build", BUILD),
            Map.entry("block-break", List.of("break")),
            Map.entry("block-place", List.of("place")),
            Map.entry("use", List.of("use")),
            Map.entry("interact", List.of("use", "interact")),
            Map.entry("damage-animals", List.of("damage.animal")),
            Map.entry("pvp", List.of("damage.player")),
            Map.entry("sleep", List.of("use.bed")),
            Map.entry("respawn-anchors", List.of("use.respawn-anchor")),
            Map.entry("tnt", List.of("world.explosion.tnt")),
            Map.entry("chest-access", List.of("use.container")),
            Map.entry("vehicle-place", List.of("vehicle.place")),
            Map.entry("vehicle-destroy", List.of("vehicle.destroy")),
            Map.entry("lighter", List.of("item.use.lighter")),
            Map.entry("ride", List.of("interact.vehicle")),
            Map.entry("potion-splash", List.of("item.use.potion")),
            Map.entry("item-frame-rotation", List.of("interact.item-frame")),
            Map.entry("block-trampling", List.of("trample")),
            Map.entry("use-anvil", List.of("use.workstation")),
            Map.entry("use-dripleaf", List.of("trample.dripleaf")),
            Map.entry("wind-charge-burst", List.of("world.explosion.breeze")),
            Map.entry("item-pickup", List.of("item.pickup")),
            Map.entry("item-drop", List.of("item.drop")),
            Map.entry("creeper-explosion", List.of("world.explosion.creeper")),
            Map.entry("enderdragon-block-damage", List.of("world.mob.other")),
            Map.entry("ghast-fireball", List.of("world.explosion.ghast")),
            Map.entry(
                    "other-explosion",
                    List.of("world.explosion.other", "world.explosion.bed", "world.explosion.crystal")),
            Map.entry("breeze-charge-explosion", List.of("world.explosion.breeze")),
            Map.entry("wither-damage", List.of("world.explosion.wither", "world.mob.wither")),
            Map.entry("enderman-grief", List.of("world.mob.enderman")),
            Map.entry("snowman-trails", List.of("world.mob.snow-golem")),
            Map.entry("ravager-grief", List.of("world.mob.ravager")),
            Map.entry("pistons", List.of("world.piston")),
            Map.entry("fire-spread", List.of("world.fire.spread")),
            Map.entry("lava-fire", List.of("world.fire.lava")),
            Map.entry("lightning", List.of("world.lightning", "world.fire.lightning")),
            Map.entry("snow-fall", List.of("world.form")),
            Map.entry("lava-harden", List.of("world.form")),
            Map.entry("entity-item-frame-destroy", List.of("world.mob.other")),
            Map.entry("entity-painting-destroy", List.of("world.mob.other")),
            Map.entry("ice-form", List.of("world.form")),
            Map.entry("frosted-ice-form", List.of("world.form")),
            Map.entry("snow-melt", List.of("world.melt")),
            Map.entry("ice-melt", List.of("world.melt")),
            Map.entry("frosted-ice-melt", List.of("world.melt")),
            Map.entry("mushroom-growth", List.of("world.spread")),
            Map.entry("leaf-decay", List.of("world.decay")),
            Map.entry("grass-growth", List.of("world.spread")),
            Map.entry("mycelium-spread", List.of("world.spread")),
            Map.entry("vine-growth", List.of("world.grow.vine")),
            Map.entry("rock-growth", List.of("world.grow.amethyst")),
            Map.entry("sculk-growth", List.of("world.grow.sculk")),
            Map.entry("crop-growth", List.of("world.grow.crop")),
            Map.entry("soil-dry", List.of("world.fade")),
            Map.entry("coral-fade", List.of("world.fade")),
            Map.entry("copper-fade", List.of("world.fade")),
            Map.entry("moisture-change", List.of("world.fade")),
            Map.entry("water-flow", List.of("world.liquid.water")),
            Map.entry("lava-flow", List.of("world.liquid.lava")),
            Map.entry("entry", List.of("move.enter")),
            Map.entry("exit", List.of("move.exit")),
            Map.entry("enderpearl", List.of("item.use.ender-pearl")),
            Map.entry("chorus-fruit-teleport", List.of("item.use.chorus-fruit")));

    /** Flags whose default region group is not everyone. */
    private static final Map<String, String> DEFAULT_GROUP = Map.of("entry", "non_members", "exit", "non_members");

    private final Path dataFolder;
    private final GuardService service;
    private final GuardBackups backups;
    private final Scheduler scheduler;
    private final Executor offTick;
    private final Consumer<String> warn;
    private final PlayerLookup players;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param dataFolder this plugin's data folder; WorldGuard's files are read from its sibling folder
     */
    public WorldGuardImporter(
            Path dataFolder,
            GuardService service,
            GuardBackups backups,
            Scheduler scheduler,
            Consumer<String> warn,
            PlayerLookup players) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.service = Objects.requireNonNull(service, "service");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.offTick = scheduler::async;
        this.warn = Objects.requireNonNull(warn, "warn");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** A block position as WorldGuard wrote it. */
    record Vec(int x, int y, int z) {}

    /** One WorldGuard region as read, before conversion. */
    record WgRegion(
            String world,
            String id,
            String type,
            @Nullable Vec min,
            @Nullable Vec max,
            List<int[]> points,
            int minY,
            int maxY,
            int priority,
            @Nullable String parent,
            Map<String, Object> flags,
            Set<UUID> owners,
            Set<UUID> members,
            Set<String> ownerGroups,
            Set<String> memberGroups,
            Set<String> ownerNames,
            Set<String> memberNames) {}

    /** Run {@code mode} from {@code source}; answers what happened, or {@link ImportKind#BUSY} while one runs. */
    public CompletableFuture<ImportOutcome> run(String mode, String source, EditContext ctx) {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new ImportOutcome(ImportKind.BUSY, 0, 0, 0, 0, 0));
        }
        CompletableFuture<ImportOutcome> future = new CompletableFuture<>();
        try {
            scheduler.async(() -> {
                CompletableFuture<ImportOutcome> work;
                try {
                    work = start(mode, source, ctx);
                } catch (Throwable failure) {
                    // Whatever start throws, an Error included, ends this run; otherwise the guard stays taken and
                    // every later import is refused as busy until a restart.
                    work = CompletableFuture.failedFuture(failure);
                }
                var unused = work.whenComplete((outcome, error) -> finish(future, outcome, error));
            });
        } catch (RuntimeException refused) {
            // The async lane refuses work while the plugin stops; nothing started, so nothing is left running.
            finish(future, null, refused);
        }
        return future;
    }

    private void finish(
            CompletableFuture<ImportOutcome> future, @Nullable ImportOutcome outcome, @Nullable Throwable error) {
        // Released before the answer goes out, so a caller that retries as soon as it hears back is not refused.
        running.set(false);
        if (error != null) {
            Throwable cause = unwrap(error);
            warn.accept("WorldGuard import failed: " + cause.getMessage());
            future.completeExceptionally(cause);
        } else {
            future.complete(Objects.requireNonNull(outcome, "outcome"));
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof UncheckedIOException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Reads what the mode needs and hands the store work to the service; runs on the async lane. */
    private CompletableFuture<ImportOutcome> start(String mode, String source, EditContext ctx)
            throws IOException, SQLException {
        Path folder = dataFolder.resolve("guard");
        Files.createDirectories(folder);
        Path batchFile = folder.resolve("import-batch.txt");
        if (mode.equals("undo")) {
            if (!Files.exists(batchFile)) {
                return CompletableFuture.completedFuture(new ImportOutcome(ImportKind.NOTHING_TO_UNDO, 0, 0, 0, 0, 0));
            }
            String batch = Files.readString(batchFile, StandardCharsets.UTF_8).strip();
            return service.removeBatch(batch, ctx)
                    .thenApplyAsync(
                            removed -> {
                                deleteIfExists(batchFile);
                                return new ImportOutcome(ImportKind.UNDONE, removed, 0, 0, 0, 0);
                            },
                            offTick);
        }
        List<WgRegion> read = source.equals("sql") ? readSql() : readYaml();
        return resolveNames(read)
                .thenComposeAsync(names -> convertAndStore(mode, source, ctx, folder, read, names), offTick);
    }

    private CompletableFuture<ImportOutcome> convertAndStore(
            String mode, String source, EditContext ctx, Path folder, List<WgRegion> read, Map<String, UUID> names) {
        String batch = "wg-" + Instant.now().toEpochMilli();
        List<String> report = new ArrayList<>();
        List<Region> converted = new ArrayList<>();
        ServerScope scope = service.config().newRegionScope();
        for (WgRegion region : read) {
            try {
                converted.add(convert(region, batch, scope, names::get, report));
            } catch (RuntimeException failure) {
                report.add(region.world() + ":" + region.id() + " skipped: " + failure.getMessage());
            }
        }
        int rules = converted.stream().mapToInt(r -> r.rules().size()).sum();
        int behaviours =
                converted.stream().mapToInt(r -> r.behaviours().asMap().size()).sum();
        List<String> header = new ArrayList<>();
        header.add("WorldGuard import " + mode + " from " + source + " at " + Instant.now());
        header.add(read.size() + " regions read, " + converted.size() + " converted, " + rules + " rules, " + behaviours
                + " behaviours, " + report.size() + " notes");
        header.add("");
        header.addAll(report);
        write(folder.resolve("import-report.txt"), header);
        if (!mode.equals("run")) {
            return CompletableFuture.completedFuture(
                    new ImportOutcome(ImportKind.DRY_RUN, converted.size(), rules, behaviours, report.size(), 0));
        }
        backups.write(service.all(), "before-import", service.config().backupsKept());
        return service.importRegions(converted, ctx)
                .thenApplyAsync(
                        skipped -> {
                            write(folder.resolve("import-batch.txt"), List.of(batch));
                            return new ImportOutcome(
                                    ImportKind.IMPORTED,
                                    converted.size() - skipped.size(),
                                    rules,
                                    behaviours,
                                    report.size(),
                                    skipped.size());
                        },
                        offTick);
    }

    /**
     * The UUID of every player the regions store by name, as far as any server of the network or this server's player
     * data knows them. Asked off the tick, so a name only the shared database holds is found.
     */
    private CompletableFuture<Map<String, UUID>> resolveNames(List<WgRegion> read) {
        Set<String> wanted = new HashSet<>();
        for (WgRegion region : read) {
            wanted.addAll(region.ownerNames());
            wanted.addAll(region.memberNames());
        }
        if (wanted.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        CompletableFuture<Map<String, UUID>> resolved = new CompletableFuture<>();
        // Off the tick, so the lookup may read the name store to settle a name WorldGuard wrote in another case. The
        // server's own cache matches any case and would hand an offline-mode server's other spelling the region.
        offTick.execute(() -> {
            try {
                Map<String, UUID> found = new HashMap<>();
                for (String name : wanted) {
                    NameMatch match = players.matchByName(name);
                    match.player().ifPresent(player -> found.put(name, player.uuid()));
                    if (match instanceof NameMatch.Ambiguous ambiguous) {
                        warn.accept("WorldGuard name " + name + " is left out: it could be any of "
                                + ambiguous.spellings());
                    }
                }
                resolved.complete(found);
            } catch (Throwable failure) {
                // The import waits on this answer; a throw has to end it rather than leave it running.
                resolved.completeExceptionally(failure);
            }
        });
        return resolved;
    }

    private static void write(Path file, List<String> lines) {
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ioFailure) {
            throw new UncheckedIOException(ioFailure);
        }
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ioFailure) {
            throw new UncheckedIOException(ioFailure);
        }
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    private List<WgRegion> readYaml() throws IOException {
        File worldsFolder = new File(dataFolder.toFile().getParentFile(), "WorldGuard/worlds");
        List<WgRegion> out = new ArrayList<>();
        File[] worlds = worldsFolder.listFiles(File::isDirectory);
        if (worlds == null) {
            throw new IOException("no WorldGuard worlds folder at " + worldsFolder);
        }
        for (File world : worlds) {
            File file = new File(world, "regions.yml");
            if (!file.isFile()) {
                continue;
            }
            out.addAll(parseYaml(world.getName(), YamlConfiguration.loadConfiguration(file)));
        }
        return out;
    }

    /** The regions of one world's {@code regions.yml}. */
    static List<WgRegion> parseYaml(String world, YamlConfiguration yaml) {
        List<WgRegion> out = new ArrayList<>();
        ConfigurationSection regions = yaml.getConfigurationSection("regions");
        if (regions == null) {
            return out;
        }
        for (String id : regions.getKeys(false)) {
            ConfigurationSection r = regions.getConfigurationSection(id);
            if (r != null) {
                out.add(fromSection(world, id, r));
            }
        }
        return out;
    }

    private static WgRegion fromSection(String world, String id, ConfigurationSection r) {
        List<int[]> points = new ArrayList<>();
        for (Map<?, ?> point : r.getMapList("points")) {
            points.add(new int[] {number(point.get("x")), number(point.get("z"))});
        }
        Map<String, Object> flags = new LinkedHashMap<>();
        ConfigurationSection f = r.getConfigurationSection("flags");
        if (f != null) {
            for (String key : f.getKeys(false)) {
                Object value = f.get(key);
                flags.put(key, value instanceof ConfigurationSection section ? section.getValues(true) : value);
            }
        }
        return new WgRegion(
                world,
                id,
                r.getString("type", "cuboid"),
                vector(r.getConfigurationSection("min")),
                vector(r.getConfigurationSection("max")),
                points,
                r.getInt("min-y"),
                r.getInt("max-y"),
                r.getInt("priority"),
                r.getString("parent"),
                flags,
                uuids(r.getStringList("owners.unique-ids")),
                uuids(r.getStringList("members.unique-ids")),
                new HashSet<>(r.getStringList("owners.groups")),
                new HashSet<>(r.getStringList("members.groups")),
                new HashSet<>(r.getStringList("owners.players")),
                new HashSet<>(r.getStringList("members.players")));
    }

    private List<WgRegion> readSql() throws IOException, SQLException {
        File configFile = new File(dataFolder.toFile().getParentFile(), "WorldGuard/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String dsn = config.getString("regions.sql.dsn", "");
        String user = config.getString("regions.sql.username", "");
        String password = config.getString("regions.sql.password", "");
        String prefix = config.getString("regions.sql.table-prefix", "");
        if (dsn.isBlank()) {
            throw new IOException("WorldGuard config.yml has no regions.sql.dsn");
        }
        // Flag values are plain scalars, lists and maps; the safe constructor builds nothing else.
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, WgBuilder> byKey = new LinkedHashMap<>();
        try (Connection c = DriverManager.getConnection(dsn, user, password)) {
            Map<Integer, String> worlds = new HashMap<>();
            try (ResultSet rs = c.createStatement().executeQuery("SELECT id, name FROM " + prefix + "world")) {
                while (rs.next()) {
                    worlds.put(rs.getInt(1), rs.getString(2));
                }
            }
            try (ResultSet rs = c.createStatement()
                    .executeQuery("SELECT id, world_id, type, priority, parent FROM " + prefix + "region")) {
                while (rs.next()) {
                    String world = worlds.getOrDefault(rs.getInt(2), "world");
                    WgBuilder b = new WgBuilder(world, rs.getString(1), rs.getString(3), rs.getInt(4), rs.getString(5));
                    byKey.put(rs.getInt(2) + ":" + rs.getString(1), b);
                }
            }
            query(
                    c,
                    "SELECT region_id, world_id, min_x, min_y, min_z, max_x, max_y, max_z FROM " + prefix
                            + "region_cuboid",
                    rs -> {
                        WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                        if (b != null) {
                            b.min = new Vec(rs.getInt(3), rs.getInt(4), rs.getInt(5));
                            b.max = new Vec(rs.getInt(6), rs.getInt(7), rs.getInt(8));
                        }
                    });
            query(c, "SELECT region_id, world_id, min_y, max_y FROM " + prefix + "region_poly2d", rs -> {
                WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                if (b != null) {
                    b.minY = rs.getInt(3);
                    b.maxY = rs.getInt(4);
                }
            });
            query(c, "SELECT region_id, world_id, x, z FROM " + prefix + "region_poly2d_point ORDER BY id", rs -> {
                WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                if (b != null) {
                    b.points.add(new int[] {rs.getInt(3), rs.getInt(4)});
                }
            });
            query(c, "SELECT region_id, world_id, flag, value FROM " + prefix + "region_flag", rs -> {
                WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                if (b != null) {
                    Object value = yaml.load(rs.getString(4));
                    if (value != null) {
                        b.flags.put(rs.getString(3), value);
                    }
                }
            });
            query(
                    c,
                    "SELECT p.region_id, p.world_id, u.uuid, u.name, p.owner FROM " + prefix + "region_players p "
                            + "JOIN " + prefix + "user u ON u.id = p.user_id",
                    rs -> {
                        WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                        if (b == null) {
                            return;
                        }
                        String uuid = rs.getString(3);
                        boolean owner = rs.getBoolean(5);
                        if (uuid != null && !uuid.isBlank()) {
                            (owner ? b.owners : b.members).add(UUID.fromString(uuid));
                        } else if (rs.getString(4) != null) {
                            (owner ? b.ownerNames : b.memberNames).add(rs.getString(4));
                        }
                    });
            query(
                    c,
                    "SELECT r.region_id, r.world_id, g.name, r.owner FROM " + prefix + "region_groups r " + "JOIN "
                            + prefix + "group g ON g.id = r.group_id",
                    rs -> {
                        WgBuilder b = byKey.get(rs.getInt(2) + ":" + rs.getString(1));
                        if (b != null) {
                            (rs.getBoolean(4) ? b.ownerGroups : b.memberGroups).add(rs.getString(3));
                        }
                    });
        }
        return byKey.values().stream().map(WgBuilder::build).toList();
    }

    private interface RowHandler {
        void row(ResultSet rs) throws SQLException;
    }

    private static void query(Connection c, String sql, RowHandler handler) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                handler.row(rs);
            }
        }
    }

    private static final class WgBuilder {
        final String world;
        final String id;
        final String type;
        final int priority;
        final @Nullable String parent;

        @Nullable Vec min;

        @Nullable Vec max;

        int minY;
        int maxY;
        final List<int[]> points = new ArrayList<>();
        final Map<String, Object> flags = new LinkedHashMap<>();
        final Set<UUID> owners = new HashSet<>();
        final Set<UUID> members = new HashSet<>();
        final Set<String> ownerGroups = new HashSet<>();
        final Set<String> memberGroups = new HashSet<>();
        final Set<String> ownerNames = new HashSet<>();
        final Set<String> memberNames = new HashSet<>();

        WgBuilder(String world, String id, String type, int priority, @Nullable String parent) {
            this.world = world;
            this.id = id;
            this.type = type;
            this.priority = priority;
            this.parent = parent;
        }

        WgRegion build() {
            return new WgRegion(
                    world,
                    id,
                    type,
                    min,
                    max,
                    points,
                    minY,
                    maxY,
                    priority,
                    parent,
                    flags,
                    owners,
                    members,
                    ownerGroups,
                    memberGroups,
                    ownerNames,
                    memberNames);
        }
    }

    // ---- converting ---------------------------------------------------------------------------------------------

    /**
     * Convert one WorldGuard region: shape, priority, parent, owners and members as roles, flags as rules or
     * behaviours. {@code names} finds the UUID of a player stored by name; anything not converted goes to the report.
     */
    static Region convert(
            WgRegion wg, String batch, ServerScope scope, Function<String, @Nullable UUID> names, List<String> report) {
        String where = wg.world() + ":" + wg.id();
        String id = wg.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        boolean global = wg.id().equals("__global__") || wg.type().equals("global");
        if (global) {
            id = "wg-global";
        }
        if (!RegionId.valid(id)) {
            throw new IllegalArgumentException("id cannot be converted: " + wg.id());
        }
        Shape shape = shape(wg, global);
        Region region = Region.create(new RegionKey(wg.world(), RegionId.of(id)), scope, shape)
                .withPriority(global ? -100_000 : wg.priority())
                .withImportBatch(batch);
        if (wg.parent() != null && !global) {
            String parent = wg.parent().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
            if (RegionId.valid(parent)) {
                region = region.withParent(RegionId.of(parent));
            }
        }
        Map<UUID, RoleName> roles = new HashMap<>();
        wg.members().forEach(u -> roles.put(u, RoleName.MEMBER));
        wg.owners().forEach(u -> roles.put(u, RoleName.OWNER));
        resolveNames(wg.memberNames(), RoleName.MEMBER, roles, where, report, names);
        resolveNames(wg.ownerNames(), RoleName.OWNER, roles, where, report, names);
        region = region.withRoles(roles);

        List<Rule> rules = new ArrayList<>();
        // WorldGuard lets only members build in a region unless passthrough is allow. The global region is the
        // other way round: everyone builds there unless passthrough is set to deny, the usual way to protect a
        // whole world.
        String passthroughFlag = String.valueOf(wg.flags().get("passthrough"));
        boolean passthrough =
                global ? !"deny".equalsIgnoreCase(passthroughFlag) : "allow".equalsIgnoreCase(passthroughFlag);
        if (!passthrough && !wg.flags().containsKey("build")) {
            for (String action : BUILD) {
                rules.add(new Rule(ActionId.of(action), new Target.Everyone(), Effect.DENY));
                rules.add(new Rule(ActionId.of(action), new Target.Role(RoleName.MEMBER), Effect.ALLOW));
                rules.add(new Rule(ActionId.of(action), new Target.Role(RoleName.OWNER), Effect.ALLOW));
                for (String group : union(wg.memberGroups(), wg.ownerGroups())) {
                    rules.add(new Rule(ActionId.of(action), new Target.Permission("group." + group), Effect.ALLOW));
                }
            }
        }
        int membership = rules.size();
        Map<BehaviourKey, String> behaviours = new EnumMap<>(BehaviourKey.class);
        for (Map.Entry<String, Object> flag : wg.flags().entrySet()) {
            String name = flag.getKey();
            if (name.endsWith("-group") || name.equals("passthrough")) {
                continue;
            }
            Object value = flag.getValue();
            List<String> actions = STATE.get(name);
            if (actions != null) {
                Effect effect = "allow".equalsIgnoreCase(String.valueOf(value))
                        ? Effect.ALLOW
                        : "deny".equalsIgnoreCase(String.valueOf(value)) ? Effect.DENY : null;
                if (effect == null) {
                    report.add(where + ": flag " + name + " has a value that is not allow or deny: " + value);
                    continue;
                }
                String group = String.valueOf(
                                wg.flags().getOrDefault(name + "-group", DEFAULT_GROUP.getOrDefault(name, "all")))
                        .toLowerCase(Locale.ROOT);
                for (Target target : targets(group, wg)) {
                    for (String action : actions) {
                        rules.add(new Rule(ActionId.of(action), target, effect));
                    }
                }
                continue;
            }
            if (!behaviour(name, value, behaviours, wg.flags())) {
                report.add(where + ": flag " + name + " = " + value + " is not converted");
            }
        }
        if (behaviours.containsKey(BehaviourKey.MESSAGE_ENTER) || behaviours.containsKey(BehaviourKey.MESSAGE_EXIT)) {
            behaviours.putIfAbsent(BehaviourKey.MESSAGE_CHANNEL, "chat");
        }
        return region.withRules(dedupe(withoutOverriddenAllows(rules, membership)))
                .withBehaviours(Behaviours.of(behaviours));
    }

    /**
     * In WorldGuard a flag set on a region beats the membership test for its actions: {@code use: allow} lets
     * everyone use, and {@code block-break: deny} stops members too. A flag denying an action to everyone therefore
     * takes away the members' allow that the membership default wrote for that action. A flag allowing it needs
     * nothing here: it replaces the default deny in the same slot when the rules are deduplicated.
     *
     * @param membership how many rules at the start of the list are the membership default
     */
    private static List<Rule> withoutOverriddenAllows(List<Rule> rules, int membership) {
        Set<String> deniedToAll = new HashSet<>();
        for (Rule rule : rules.subList(membership, rules.size())) {
            if (rule.effect() == Effect.DENY && rule.target() instanceof Target.Everyone) {
                deniedToAll.add(rule.action().value());
            }
        }
        if (deniedToAll.isEmpty()) {
            return rules;
        }
        List<Rule> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            boolean overridden = i < membership
                    && rule.effect() == Effect.ALLOW
                    && deniedToAll.contains(rule.action().value());
            if (!overridden) {
                out.add(rule);
            }
        }
        return out;
    }

    private static boolean behaviour(
            String name, @Nullable Object value, Map<BehaviourKey, String> out, Map<String, Object> flags) {
        String text = value == null ? "" : String.valueOf(value);
        switch (name) {
            case "greeting" -> out.put(BehaviourKey.MESSAGE_ENTER, legacy(text));
            case "farewell" -> out.put(BehaviourKey.MESSAGE_EXIT, legacy(text));
            case "greeting-title" -> {
                out.put(BehaviourKey.MESSAGE_ENTER, legacy(text.replace("\n", "|")));
                out.put(BehaviourKey.MESSAGE_CHANNEL, "title");
            }
            case "farewell-title" -> {
                out.put(BehaviourKey.MESSAGE_EXIT, legacy(text.replace("\n", "|")));
                out.put(BehaviourKey.MESSAGE_CHANNEL, "title");
            }
            case "entry-deny-message" -> out.put(BehaviourKey.ENTER_DENY_MESSAGE, legacy(text));
            case "exit-deny-message" -> out.put(BehaviourKey.EXIT_DENY_MESSAGE, legacy(text));
            case "game-mode" -> out.put(BehaviourKey.GAME_MODE, text.toLowerCase(Locale.ROOT));
            case "invincible" -> out.put(BehaviourKey.INVINCIBLE, String.valueOf("allow".equalsIgnoreCase(text)));
            case "time-lock" -> out.put(BehaviourKey.TIME, text);
            case "weather-lock" -> out.put(BehaviourKey.WEATHER, text.equalsIgnoreCase("downfall") ? "rain" : "clear");
            case "blocked-cmds" -> out.put(BehaviourKey.COMMANDS_DENY, commands(value));
            case "allowed-cmds" -> out.put(BehaviourKey.COMMANDS_ALLOW, commands(value));
            case "heal-amount" ->
                out.put(BehaviourKey.HEAL, text + " " + Math.max(1, number(flags.getOrDefault("heal-delay", 1))));
            case "feed-amount" ->
                out.put(BehaviourKey.FEED, text + " " + Math.max(1, number(flags.getOrDefault("feed-delay", 1))));
            case "heal-delay", "feed-delay" -> {
                // Read together with the amount above.
            }
            case "fall-damage" -> out.put(BehaviourKey.FALL_DAMAGE, String.valueOf(!"deny".equalsIgnoreCase(text)));
            case "firework-damage" ->
                out.put(BehaviourKey.FIREWORK_DAMAGE, String.valueOf(!"deny".equalsIgnoreCase(text)));
            case "natural-hunger-drain" -> out.put(BehaviourKey.HUNGER, String.valueOf(!"deny".equalsIgnoreCase(text)));
            case "exp-drops" -> out.put(BehaviourKey.EXP_DROPS, String.valueOf(!"deny".equalsIgnoreCase(text)));
            case "deny-message" -> {
                // An empty message is how a WorldGuard region goes quiet; a worded one stays the server's own.
                if (!text.isBlank()) {
                    return false;
                }
                out.put(BehaviourKey.DENY_MESSAGES, "false");
            }
            case "mob-spawning" -> {
                if ("deny".equalsIgnoreCase(text)) {
                    out.put(BehaviourKey.SPAWN_DENY, "*");
                }
            }
            case "deny-spawn" ->
                out.put(BehaviourKey.SPAWN_DENY, commands(value).replace("minecraft:", ""));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static List<Target> targets(String group, WgRegion wg) {
        List<Target> out = new ArrayList<>();
        switch (group) {
            case "members" -> {
                out.add(new Target.Role(RoleName.MEMBER));
                out.add(new Target.Role(RoleName.OWNER));
                union(wg.memberGroups(), wg.ownerGroups()).forEach(g -> out.add(new Target.Permission("group." + g)));
            }
            case "owners" -> {
                out.add(new Target.Role(RoleName.OWNER));
                wg.ownerGroups().forEach(g -> out.add(new Target.Permission("group." + g)));
            }
            case "non_owners", "nonowners" -> {
                out.add(new Target.NonMember());
                out.add(new Target.Role(RoleName.MEMBER));
            }
            case "non_members", "nonmembers" -> out.add(new Target.NonMember());
            case "none" -> {
                // Nobody: the flag does nothing.
            }
            default -> out.add(new Target.Everyone());
        }
        return out;
    }

    private static Shape shape(WgRegion wg, boolean global) {
        if (global) {
            return new Shape.Global();
        }
        if (wg.type().equalsIgnoreCase("poly2d")) {
            int[] xs = wg.points().stream().mapToInt(p -> p[0]).toArray();
            int[] zs = wg.points().stream().mapToInt(p -> p[1]).toArray();
            return Shape.Polygon.of(xs, zs, wg.minY(), wg.maxY());
        }
        Vec min = Objects.requireNonNull(wg.min(), "min");
        Vec max = Objects.requireNonNull(wg.max(), "max");
        return new Shape.Cuboid(Bounds.of(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
    }

    private static void resolveNames(
            Set<String> wanted,
            RoleName role,
            Map<UUID, RoleName> roles,
            String where,
            List<String> report,
            Function<String, @Nullable UUID> names) {
        for (String name : wanted) {
            UUID id = names.apply(name);
            if (id == null) {
                report.add(where + ": player name " + name + " has no known UUID and was left out");
            } else {
                roles.putIfAbsent(id, role);
            }
        }
    }

    private static List<Rule> dedupe(List<Rule> rules) {
        Map<String, Rule> bySlot = new LinkedHashMap<>();
        for (Rule rule : rules) {
            bySlot.put(rule.action().value() + " " + rule.target().text(), rule);
        }
        return List.copyOf(bySlot.values());
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static String commands(@Nullable Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable<?> list) {
            for (Object entry : list) {
                out.add(String.valueOf(entry).replaceFirst("^/", "").strip().toLowerCase(Locale.ROOT));
            }
        } else if (value != null) {
            for (String entry : String.valueOf(value).split(",", -1)) {
                out.add(entry.replaceFirst("^/", "").strip().toLowerCase(Locale.ROOT));
            }
        }
        return String.join(",", out);
    }

    /** WorldGuard messages use {@code &} codes; turn the common ones into MiniMessage tags. */
    private static String legacy(String text) {
        String[][] codes = {
            {"&0", "<black>"},
            {"&1", "<dark_blue>"},
            {"&2", "<dark_green>"},
            {"&3", "<dark_aqua>"},
            {"&4", "<dark_red>"},
            {"&5", "<dark_purple>"},
            {"&6", "<gold>"},
            {"&7", "<gray>"},
            {"&8", "<dark_gray>"},
            {"&9", "<blue>"},
            {"&a", "<green>"},
            {"&b", "<aqua>"},
            {"&c", "<red>"},
            {"&d", "<light_purple>"},
            {"&e", "<yellow>"},
            {"&f", "<white>"},
            {"&l", "<bold>"},
            {"&o", "<italic>"},
            {"&n", "<underlined>"},
            {"&m", "<strikethrough>"},
            {"&r", "<reset>"}
        };
        String out = text;
        for (String[] code : codes) {
            out = out.replace(code[0], code[1]).replace(code[0].toUpperCase(Locale.ROOT), code[1]);
        }
        return out;
    }

    private static @Nullable Vec vector(@Nullable ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new Vec(section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    private static Set<UUID> uuids(List<String> raw) {
        Set<UUID> out = new HashSet<>();
        for (String value : raw) {
            try {
                out.add(UUID.fromString(value));
            } catch (IllegalArgumentException skipped) {
                // Not a UUID; WorldGuard wrote names in older files, handled separately.
            }
        }
        return out;
    }

    private static int number(@Nullable Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
