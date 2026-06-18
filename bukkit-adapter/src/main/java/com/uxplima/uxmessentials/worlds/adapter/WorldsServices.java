package com.uxplima.uxmessentials.worlds.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
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
    private final WorldRepository repository;
    private final Scheduler scheduler;
    private final Supplier<Set<WorldName>> onDiskScanner;
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
            WorldRepository repository,
            Scheduler scheduler,
            Supplier<Set<WorldName>> onDiskScanner) {
        this.createWorld = Objects.requireNonNull(createWorld, "createWorld");
        this.importWorld = Objects.requireNonNull(importWorld, "importWorld");
        this.loadWorld = Objects.requireNonNull(loadWorld, "loadWorld");
        this.unloadWorld = Objects.requireNonNull(unloadWorld, "unloadWorld");
        this.unregisterWorld = Objects.requireNonNull(unregisterWorld, "unregisterWorld");
        this.deleteWorld = Objects.requireNonNull(deleteWorld, "deleteWorld");
        this.listWorlds = Objects.requireNonNull(listWorlds, "listWorlds");
        this.worldInfo = Objects.requireNonNull(worldInfo, "worldInfo");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.onDiskScanner = Objects.requireNonNull(onDiskScanner, "onDiskScanner");
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
