package com.uxplima.uxmessentials.worlds.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainView;
import com.uxplima.uxmessentials.worlds.application.BackupWorld;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListBackups;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.PregenWorld;
import com.uxplima.uxmessentials.worlds.application.RestoreWorld;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The started worlds module's use-case holder for its inbound adapters, plus the import-folder snapshot
 * the {@code /worlds import} tab-completion reads. The snapshot is refreshed off-tick (a folder scan is
 * filesystem I/O) so the command thread only ever reads the cached {@link AtomicReference}.
 */
@NullMarked
public final class WorldsServices {

    private final CreateWorld createWorld;
    private final ImportWorld importWorld;
    private final LoadWorld loadWorld;
    private final UnloadWorld unloadWorld;
    private final UnregisterWorld unregisterWorld;
    private final DeleteWorld deleteWorld;
    private final ListWorlds listWorlds;
    private final WorldInfo worldInfo;
    private final SetWorldProperty setWorldProperty;
    private final SetGamerule setGamerule;
    private final SetWorldSpawn setWorldSpawn;
    private final SetWorldAlias setWorldAlias;
    private final PregenWorld pregen;
    private final WorldTeleportService worldTeleport;
    private final BackupWorld backupWorld;
    private final ListBackups listBackups;
    private final RestoreWorld restoreWorld;
    private final GameRuleCatalog gameRuleCatalog;
    private final WorldRepository repository;
    private final Scheduler scheduler;
    private final Supplier<Set<WorldName>> onDiskScanner;
    private final BiConsumer<Player, PlayerRef> openWorldList;
    private final WorldMainView worldMainView;
    private final AtomicReference<List<String>> importable = new AtomicReference<>(List.of());

    public WorldsServices(
            CreateWorld createWorld,
            ImportWorld importWorld,
            LoadWorld loadWorld,
            UnloadWorld unloadWorld,
            UnregisterWorld unregisterWorld,
            DeleteWorld deleteWorld,
            ListWorlds listWorlds,
            WorldInfo worldInfo,
            SetWorldProperty setWorldProperty,
            SetGamerule setGamerule,
            SetWorldSpawn setWorldSpawn,
            SetWorldAlias setWorldAlias,
            PregenWorld pregen,
            GameRuleCatalog gameRuleCatalog,
            WorldRepository repository,
            Scheduler scheduler,
            Supplier<Set<WorldName>> onDiskScanner,
            WorldTeleportService worldTeleport,
            BackupWorld backupWorld,
            ListBackups listBackups,
            RestoreWorld restoreWorld,
            BiConsumer<Player, PlayerRef> openWorldList,
            WorldMainView worldMainView) {
        this.createWorld = Objects.requireNonNull(createWorld, "createWorld");
        this.importWorld = Objects.requireNonNull(importWorld, "importWorld");
        this.loadWorld = Objects.requireNonNull(loadWorld, "loadWorld");
        this.unloadWorld = Objects.requireNonNull(unloadWorld, "unloadWorld");
        this.unregisterWorld = Objects.requireNonNull(unregisterWorld, "unregisterWorld");
        this.deleteWorld = Objects.requireNonNull(deleteWorld, "deleteWorld");
        this.listWorlds = Objects.requireNonNull(listWorlds, "listWorlds");
        this.worldInfo = Objects.requireNonNull(worldInfo, "worldInfo");
        this.setWorldProperty = Objects.requireNonNull(setWorldProperty, "setWorldProperty");
        this.setGamerule = Objects.requireNonNull(setGamerule, "setGamerule");
        this.setWorldSpawn = Objects.requireNonNull(setWorldSpawn, "setWorldSpawn");
        this.setWorldAlias = Objects.requireNonNull(setWorldAlias, "setWorldAlias");
        this.pregen = Objects.requireNonNull(pregen, "pregen");
        this.gameRuleCatalog = Objects.requireNonNull(gameRuleCatalog, "gameRuleCatalog");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.onDiskScanner = Objects.requireNonNull(onDiskScanner, "onDiskScanner");
        this.worldTeleport = Objects.requireNonNull(worldTeleport, "worldTeleport");
        this.backupWorld = Objects.requireNonNull(backupWorld, "backupWorld");
        this.listBackups = Objects.requireNonNull(listBackups, "listBackups");
        this.restoreWorld = Objects.requireNonNull(restoreWorld, "restoreWorld");
        this.openWorldList = Objects.requireNonNull(openWorldList, "openWorldList");
        this.worldMainView = Objects.requireNonNull(worldMainView, "worldMainView");
    }

    public CreateWorld createWorld() {
        return createWorld;
    }

    public ImportWorld importWorld() {
        return importWorld;
    }

    public LoadWorld loadWorld() {
        return loadWorld;
    }

    public UnloadWorld unloadWorld() {
        return unloadWorld;
    }

    public UnregisterWorld unregisterWorld() {
        return unregisterWorld;
    }

    public DeleteWorld deleteWorld() {
        return deleteWorld;
    }

    public ListWorlds listWorlds() {
        return listWorlds;
    }

    public WorldInfo worldInfo() {
        return worldInfo;
    }

    public SetWorldProperty setWorldProperty() {
        return setWorldProperty;
    }

    public SetGamerule setGamerule() {
        return setGamerule;
    }

    public SetWorldSpawn setWorldSpawn() {
        return setWorldSpawn;
    }

    public SetWorldAlias setWorldAlias() {
        return setWorldAlias;
    }

    /** The {@code /worlds pregen} use case, exposed so the command can start and cancel pre-generation. */
    public PregenWorld pregen() {
        return pregen;
    }

    public WorldTeleportService worldTeleport() {
        return worldTeleport;
    }

    /** The {@code /worlds backup} use case, exposed so the command can kick a snapshot. */
    public BackupWorld backupWorld() {
        return backupWorld;
    }

    /** The {@code /worlds backups} use case, exposed so the command can list a world's stored snapshots. */
    public ListBackups listBackups() {
        return listBackups;
    }

    /** The {@code /worlds restore}/{@code restoreconfirm} use case, exposed so the command can stage and confirm. */
    public RestoreWorld restoreWorld() {
        return restoreWorld;
    }

    /**
     * Open the engine-rendered world picker for {@code viewer}, the menu {@code /worlds editor} / {@code /world gui}
     * launches. Exposed as a seam so the command opens the list without depending on the menu engine directly; worlds
     * wiring binds it to {@code WorldListMenu.open} once that menu is built.
     */
    public void openWorldList(Player player, PlayerRef viewer) {
        openWorldList.accept(player, viewer);
    }

    /** The per-world hub view {@code /worlds editor <world>} opens, exposed so the command can launch it. */
    public WorldMainView worldMainView() {
        return worldMainView;
    }

    /** The server's gamerule names for {@code /world gamerule} tab-completion. */
    public List<String> gameRuleNames() {
        return gameRuleCatalog.names();
    }

    public WorldRepository repository() {
        return repository;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    /** The candidate import folders from the last refresh (on-disk worlds not yet registered). */
    public List<String> importableFolders() {
        // The reference is seeded non-null and only ever set to a non-null list; the cast documents that to
        // NullAway, which cannot see the invariant through AtomicReference.
        return Objects.requireNonNull(importable.get());
    }

    /** Async rescan of candidate import folders (on-disk minus already-registered), never on tick. */
    public void refreshImportableFolders() {
        scheduler.async(() -> {
            Set<WorldName> registered =
                    repository.all().stream().map(ManagedWorld::name).collect(Collectors.toSet());
            List<String> candidates = onDiskScanner.get().stream()
                    .filter(name -> !registered.contains(name))
                    .map(WorldName::value)
                    .sorted()
                    .toList();
            importable.set(candidates);
        });
    }
}
