package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.WorldArchive;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.BackupRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldArchive} adapter: orchestrates a world's backup and restore across the tick boundary.
 *
 * <p>A backup runs save → zip → prune: the live {@code World#save()} happens on the region thread (the
 * use case calls {@link #backup} via the command's {@code onGlobal}), then the long-running zip and the
 * prune of older archives run off-tick through the {@code Scheduler}'s async context, and the completion
 * notification bounces back onto the operator's entity thread.
 *
 * <p>A restore is the dangerous half — it deletes the world folder — so it validates before it destroys:
 * the archive file must exist and the world must be managed (needed to reload it with its spec) <em>before</em>
 * any player is evacuated, the world is unloaded, or the folder is touched. Evacuation and the unload run on
 * the region thread; the delete-tree and unzip run off-tick; the reload returns to the global thread. A
 * missing archive or unmanaged world is rejected with the folder left exactly as it was.
 */
@NullMarked
public final class BukkitWorldArchive implements WorldArchive {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Server server;
    private final Scheduler scheduler;
    private final WorldEngine engine;
    private final WorldRepository repository;
    private final WorldArchiver archiver;
    private final WorldTeleportService teleporter;
    private final ForcedWorldEntryMarker marker;
    private final WorldsSettings settings;
    private final WorldNotifier notifier;
    private final Logger log;
    private final Path dataFolder;

    public BukkitWorldArchive(
            Server server,
            Scheduler scheduler,
            WorldEngine engine,
            WorldRepository repository,
            WorldArchiver archiver,
            WorldTeleportService teleporter,
            ForcedWorldEntryMarker marker,
            WorldsSettings settings,
            WorldNotifier notifier,
            Logger log,
            Path dataFolder) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.archiver = Objects.requireNonNull(archiver, "archiver");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.marker = Objects.requireNonNull(marker, "marker");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    @Override
    public Result<BackupId, WorldError> backup(PlayerRef initiator, WorldName world) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(world, "world");
        BackupId id = new BackupId(STAMP.format(Instant.now()));
        World w = server.getWorld(world.value());
        if (w != null) {
            w.save(); // region thread — flush the live world to disk before we zip it
        }
        scheduler.async(() -> doBackup(initiator, world, id));
        return Result.ok(id);
    }

    /** Off-tick: zip the world folder, prune older archives, and notify the initiator of the outcome. */
    private void doBackup(PlayerRef initiator, WorldName world, BackupId id) {
        try {
            archiver.zip(worldFolder(world), archiveFile(world, id));
            prune(world);
            notify(
                    initiator,
                    WorldsMessageKey.WORLD_BACKUP_CREATED,
                    Map.of("world", world.value(), "backup", id.value()));
        } catch (IOException e) {
            log.error("backup of " + world.value() + " failed", e);
            notify(initiator, WorldsMessageKey.WORLD_BACKUP_FAILED, Map.of("world", world.value()));
        }
    }

    @Override
    public List<BackupRef> list(WorldName world) {
        Objects.requireNonNull(world, "world");
        Path dir = backupsDir(world);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .map(this::toRef)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(BackupRef::createdAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("listing backups of " + world.value() + " failed", e);
            return List.of();
        }
    }

    /** Map a backup archive path to a {@link BackupRef}, skipping a file whose stem is not a valid id. */
    private Optional<BackupRef> toRef(Path archive) {
        String fileName = archive.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - ".zip".length());
        try {
            BackupId id = new BackupId(stem);
            Instant createdAt = Files.getLastModifiedTime(archive).toInstant();
            return Optional.of(new BackupRef(id, createdAt, Files.size(archive)));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // a malformed file name is not one of our archives
        } catch (IOException e) {
            log.error("reading backup metadata of " + archive + " failed", e);
            return Optional.empty();
        }
    }

    /** Delete the oldest archives beyond the retention count; a prune failure never fails the backup. */
    private void prune(WorldName world) {
        List<BackupRef> all = list(world);
        int keep = settings.backupRetentionCount();
        if (all.size() <= keep) {
            return;
        }
        for (BackupRef ref : all.subList(keep, all.size())) { // list is newest-first, so this is the oldest
            try {
                Files.deleteIfExists(archiveFile(world, ref.id()));
            } catch (IOException e) {
                log.error("pruning backup " + ref.id().value() + " of " + world.value() + " failed", e);
            }
        }
    }

    @Override
    public Result<Unit, WorldError> restore(PlayerRef initiator, WorldName world, BackupId id) {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        Path archive = archiveFile(world, id);
        if (!Files.isRegularFile(archive)) {
            return Result.err(WorldError.BACKUP_NOT_FOUND); // validate before we destroy anything
        }
        Optional<ManagedWorld> managed = repository.find(world);
        if (managed.isEmpty()) {
            return Result.err(WorldError.NOT_FOUND); // need the spec to reload the world afterwards
        }
        World w = server.getWorld(world.value());
        if (w != null) {
            evacuate(initiator, w);
        }
        engine.unload(world, false); // discard live state — we are replacing the folder
        scheduler.async(() -> swapAndReload(initiator, world, id, archive, managed.get()));
        return Result.ok();
    }

    /** Region thread: send every player in {@code w} to the default world, marking each as a forced entry. */
    private void evacuate(PlayerRef initiator, World w) {
        engine.defaultWorldName().ifPresent(def -> {
            for (Player p : List.copyOf(w.getPlayers())) {
                marker.mark(p.getUniqueId());
                teleporter.forced(initiator, BukkitRefs.toRef(p), def);
            }
        });
    }

    /** Off-tick: replace the world folder from the archive, then reload the world back on the global thread. */
    private void swapAndReload(PlayerRef initiator, WorldName world, BackupId id, Path archive, ManagedWorld managed) {
        try {
            archiver.deleteTree(worldFolder(world));
            archiver.unzip(archive, worldFolder(world));
            scheduler.onGlobal(() -> finishRestore(initiator, world, id, managed));
        } catch (IOException e) {
            log.error("restore of " + world.value() + " failed", e);
            notify(initiator, WorldsMessageKey.WORLD_RESTORE_FAILED, Map.of("world", world.value()));
        }
    }

    /** Global thread: load the restored world with its spec and notify the initiator it is back. */
    private void finishRestore(PlayerRef initiator, WorldName world, BackupId id, ManagedWorld managed) {
        engine.load(managed);
        notify(initiator, WorldsMessageKey.WORLD_RESTORED, Map.of("world", world.value(), "backup", id.value()));
    }

    /** Folia-safe notify: deliver the completion message on the operator's own entity thread. */
    private void notify(PlayerRef ref, MessageKey key, Map<String, String> placeholders) {
        scheduler.onEntity(ref, () -> notifier.send(ref, key, placeholders));
    }

    private Path worldFolder(WorldName name) {
        return server.getWorldContainer().toPath().resolve(name.value());
    }

    private Path backupsDir(WorldName name) {
        return dataFolder.resolve(settings.backupDirectory()).resolve(name.value());
    }

    private Path archiveFile(WorldName world, BackupId id) {
        return backupsDir(world).resolve(id.value() + ".zip");
    }
}
