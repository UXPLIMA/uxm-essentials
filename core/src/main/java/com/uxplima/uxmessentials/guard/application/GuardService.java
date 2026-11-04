package com.uxplima.uxmessentials.guard.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.application.port.GuardSync;
import com.uxplima.uxmessentials.guard.application.port.GuardWorlds;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.engine.Compiler;
import com.uxplima.uxmessentials.guard.domain.engine.GuardSnapshot;
import com.uxplima.uxmessentials.guard.domain.engine.WorldIndex;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.jspecify.annotations.Nullable;

/**
 * The guard's live state and the one place regions change.
 *
 * <p>Listeners read {@link #snapshot()}, a value swapped atomically, and never wait. Every edit runs on the async
 * lane under one lock: validate, store the change with its history entry, update the in-memory regions, recompile
 * the touched world, swap the snapshot, tell peers. A store that cannot be read on load locks every world so
 * nothing is left unprotected.
 */
public final class GuardService {

    private final GuardStore store;
    private final GuardSync sync;
    private final GuardWorlds worlds;
    private final Scheduler scheduler;
    private final Consumer<String> warn;
    private final Object lock = new Object();
    private final Map<RegionKey, Region> regions = new ConcurrentHashMap<>();
    private volatile GuardSnapshot snapshot;
    private final List<Consumer<GuardSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private volatile ActionTree tree;
    private volatile @Nullable ActionTree compiledTree;
    private volatile GuardConfig config;
    private volatile boolean loadFailed;
    private volatile long stamp;

    public GuardService(
            GuardStore store,
            GuardSync sync,
            GuardWorlds worlds,
            Scheduler scheduler,
            GuardConfig config,
            Consumer<String> warn) {
        this.store = Objects.requireNonNull(store, "store");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        this.warn = Objects.requireNonNull(warn, "warn");
        this.tree = ActionTree.builtIn();
        this.snapshot = GuardSnapshot.empty(tree);
    }

    /** What every check reads. */
    public GuardSnapshot snapshot() {
        return snapshot;
    }

    public GuardConfig config() {
        return config;
    }

    /** The action tree, built-ins plus every branch registered by other plugins. */
    public ActionTree tree() {
        return tree;
    }

    /** True when the last load failed and every world is locked. */
    public boolean loadFailed() {
        return loadFailed;
    }

    /** Called with every new snapshot, on whichever thread swapped it. */
    public void onSnapshot(Consumer<GuardSnapshot> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Load every region of this server's scopes and compile; async. */
    public CompletableFuture<Void> load() {
        return run(() -> {
            synchronized (lock) {
                loadLocked();
            }
            return null;
        });
    }

    /** Swap the config and reload everything; async. */
    public CompletableFuture<Void> reload(GuardConfig next) {
        this.config = Objects.requireNonNull(next, "next");
        return load();
    }

    /** Compile a world that just loaded. */
    public void worldLoaded(String world) {
        scheduler.async(() -> {
            synchronized (lock) {
                compileWorld(world);
            }
        });
    }

    /** Forget a world that unloaded. */
    public void worldUnloaded(String world) {
        synchronized (lock) {
            swap(snapshot.without(world));
        }
    }

    /** Add action branches for another plugin and recompile every world. */
    public void registerActions(Collection<ActionId> ids) {
        scheduler.async(() -> {
            synchronized (lock) {
                tree = tree.with(ids);
                compileAll();
            }
        });
    }

    /** The regions of one world on this server, by id. */
    public List<Region> regions(String world) {
        List<Region> out = new ArrayList<>();
        for (Region region : regions.values()) {
            if (region.world().equals(world)) {
                out.add(region);
            }
        }
        out.sort(Comparator.comparing(Region::id));
        return out;
    }

    /** Every region on this server. */
    public List<Region> all() {
        List<Region> out = new ArrayList<>(regions.values());
        out.sort(Comparator.comparing(Region::world).thenComparing(Region::id));
        return out;
    }

    public Optional<Region> region(RegionKey key) {
        return Optional.ofNullable(regions.get(key));
    }

    /** Regions with a tag, across worlds. */
    public List<Region> tagged(String tag) {
        return all().stream().filter(r -> r.tags().contains(tag)).toList();
    }

    /** Regions whose bounds come within {@code radius} blocks of a point, nearest first. */
    public List<Region> near(String world, int x, int y, int z, int radius) {
        Bounds around = new Bounds(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        return regions(world).stream()
                .filter(r -> !(r.shape() instanceof Shape.Global)
                        && r.shape().bounds().intersects(around))
                .sorted(Comparator.comparingLong(r -> distanceSq(r.shape().bounds(), x, y, z)))
                .toList();
    }

    /** Create a region; refused when the id is taken in that world. */
    public CompletableFuture<Result<Region, GuardError>> create(
            RegionKey key, Shape shape, @Nullable GuardScope scope, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                if (worlds.height(key.world()).isEmpty()) {
                    return Result.err(GuardError.UNKNOWN_WORLD);
                }
                if (worldLocked(key.world())) {
                    return Result.err(GuardError.WORLD_LOCKED);
                }
                if (regions.containsKey(key)) {
                    return Result.err(GuardError.ALREADY_EXISTS);
                }
                Region created = Region.create(key, scope == null ? config.newRegionScope() : scope, shape);
                return commit(null, created, ctx, "create");
            }
        });
    }

    /** Apply one edit to an existing region. */
    public CompletableFuture<Result<Region, GuardError>> edit(
            RegionKey key, String change, UnaryOperator<Region> edit, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                return editLocked(key, change, edit, ctx);
            }
        });
    }

    /** Apply one edit to every region with {@code tag}; answers how many changed. */
    public CompletableFuture<Result<Integer, GuardError>> editTagged(
            String tag, String change, UnaryOperator<Region> edit, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                int changed = 0;
                for (Region region : tagged(tag)) {
                    Result<Region, GuardError> result = editLocked(region.key(), change, edit, ctx);
                    if (result.isOk()) {
                        changed++;
                    } else if (result.errorOrThrow() == GuardError.STORE_FAILED) {
                        return Result.err(GuardError.STORE_FAILED);
                    }
                }
                return Result.ok(changed);
            }
        });
    }

    /** Remove a region; its children lose their parent. */
    public CompletableFuture<Result<Region, GuardError>> remove(RegionKey key, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                Region current = regions.get(key);
                if (current == null) {
                    return Result.err(GuardError.NO_SUCH_REGION);
                }
                if (current.locked() && !ctx.mayEditLocked()) {
                    return Result.err(GuardError.REGION_LOCKED);
                }
                for (Region child : regions(key.world())) {
                    if (key.id().equals(child.parent())) {
                        Result<Region, GuardError> orphaned = commit(child, child.withParent(null), ctx, "parent");
                        if (orphaned.isErr()) {
                            return orphaned;
                        }
                    }
                }
                Result<Region, GuardError> removed = commit(current, null, ctx, "remove");
                return removed.isOk() ? Result.ok(current) : removed;
            }
        });
    }

    /** Bring back the most recently removed region with this key. */
    public CompletableFuture<Result<Region, GuardError>> restore(RegionKey key, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                if (regions.containsKey(key)) {
                    return Result.err(GuardError.ALREADY_EXISTS);
                }
                for (HistoryEntry entry : store.history(key, 0, 200)) {
                    if (entry.after() == null && entry.before() != null) {
                        return commit(null, entry.before(), ctx, "restore");
                    }
                }
                return Result.err(GuardError.NOTHING_TO_UNDO);
            }
        });
    }

    /** Undo one history entry: put back the region as it was before that change. */
    public CompletableFuture<Result<Region, GuardError>> undo(long historyId, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                Optional<HistoryEntry> found = store.historyEntry(historyId);
                if (found.isEmpty()) {
                    return Result.err(GuardError.NOTHING_TO_UNDO);
                }
                HistoryEntry entry = found.get();
                Region current = regions.get(entry.key());
                if (current != null && current.locked() && !ctx.mayEditLocked()) {
                    return Result.err(GuardError.REGION_LOCKED);
                }
                Region before = entry.before();
                if (before == null) {
                    if (current == null) {
                        return Result.err(GuardError.NOTHING_TO_UNDO);
                    }
                    Result<Region, GuardError> removed = commit(current, null, ctx, "undo");
                    return removed.isOk() ? Result.ok(current) : removed;
                }
                return commit(current, before, ctx, "undo");
            }
        });
    }

    /** The history of one region, or of all when {@code key} is null, newest first. */
    public CompletableFuture<List<HistoryEntry>> history(@Nullable RegionKey key, int offset, int limit) {
        return run(() -> store.history(key, offset, limit));
    }

    /**
     * Store a batch of new regions in one go (the WorldGuard import). Existing ids are skipped and reported back.
     */
    public CompletableFuture<List<RegionKey>> importRegions(List<Region> incoming, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                List<RegionKey> skipped = new ArrayList<>();
                Set<String> touched = new HashSet<>();
                for (Region region : incoming) {
                    if (regions.containsKey(region.key())) {
                        skipped.add(region.key());
                        continue;
                    }
                    store.write(null, region, ctx.actor(), "import");
                    regions.put(region.key(), region);
                    touched.add(region.world());
                    sync.changed(region.scope(), region.key());
                }
                touched.forEach(this::compileWorld);
                return List.copyOf(skipped);
            }
        });
    }

    /** Remove every region an import batch created; answers how many. */
    public CompletableFuture<Integer> removeBatch(String batch, EditContext ctx) {
        return run(() -> {
            synchronized (lock) {
                int removed = 0;
                Set<String> touched = new HashSet<>();
                for (Region region : List.copyOf(regions.values())) {
                    if (batch.equals(region.importBatch())) {
                        store.write(region, null, ctx.actor(), "import-undo");
                        regions.remove(region.key());
                        touched.add(region.world());
                        sync.changed(region.scope(), region.key());
                        removed++;
                    }
                }
                touched.forEach(this::compileWorld);
                return removed;
            }
        });
    }

    /** A peer changed a region: read it again and recompile its world. */
    public void remoteChanged(GuardScope scope, RegionKey key) {
        if (!config.loadedScopes().contains(scope)) {
            return;
        }
        scheduler.async(() -> {
            synchronized (lock) {
                Optional<Region> fresh = store.find(scope, key);
                Region old = regions.get(key);
                if (fresh.isPresent()
                        && config.loadedScopes().contains(fresh.get().scope())) {
                    regions.put(key, fresh.get());
                } else if (old != null && old.scope().equals(scope)) {
                    regions.remove(key);
                }
                compileWorld(key.world());
            }
        });
    }

    /** Without Redis: reload everything when the store's stamp moved. Runs on the async lane. */
    public void poll() {
        scheduler.async(() -> {
            long now;
            try {
                now = store.stamp(Set.copyOf(config.loadedScopes()));
            } catch (RuntimeException e) {
                return;
            }
            if (now != stamp) {
                synchronized (lock) {
                    loadLocked();
                }
            }
        });
    }

    private Result<Region, GuardError> editLocked(
            RegionKey key, String change, UnaryOperator<Region> edit, EditContext ctx) {
        Region current = regions.get(key);
        if (current == null) {
            return Result.err(GuardError.NO_SUCH_REGION);
        }
        if (worldLocked(key.world())) {
            return Result.err(GuardError.WORLD_LOCKED);
        }
        Region next;
        try {
            next = edit.apply(current);
        } catch (IllegalArgumentException invalid) {
            return Result.err(GuardError.INVALID_PARENT);
        }
        if ((current.locked() || next.locked() != current.locked()) && !ctx.mayEditLocked()) {
            return Result.err(GuardError.REGION_LOCKED);
        }
        if (!next.key().equals(key)) {
            throw new IllegalStateException("an edit must not change the region key");
        }
        if (next.parent() != null && !validParent(next)) {
            return Result.err(GuardError.INVALID_PARENT);
        }
        if (next.template() != null && !config.templates().containsKey(next.template())) {
            return Result.err(GuardError.UNKNOWN_TEMPLATE);
        }
        for (Rule rule : next.rules()) {
            if (!tree.contains(rule.action())) {
                return Result.err(GuardError.UNKNOWN_ACTION);
            }
        }
        if (next.equals(current)) {
            return Result.ok(current);
        }
        return commit(current, next, ctx, change);
    }

    private boolean validParent(Region region) {
        RegionId parent = region.parent();
        if (parent == null) {
            return true;
        }
        Map<RegionId, Region> byId = new HashMap<>();
        for (Region r : regions(region.world())) {
            byId.put(r.id(), r);
        }
        byId.put(region.id(), region);
        if (!byId.containsKey(parent)) {
            return false;
        }
        Set<RegionId> seen = new HashSet<>();
        for (Region at = region; at != null; ) {
            if (!seen.add(at.id())) {
                return false;
            }
            RegionId up = at.parent();
            at = up == null ? null : byId.get(up);
        }
        return true;
    }

    private Result<Region, GuardError> commit(
            @Nullable Region before, @Nullable Region after, EditContext ctx, String change) {
        try {
            store.write(before, after, ctx.actor(), change);
        } catch (RuntimeException e) {
            warn.accept("guard: could not store a change to " + (after != null ? after.key() : before) + ": "
                    + e.getMessage());
            return Result.err(GuardError.STORE_FAILED);
        }
        Region subject = after != null ? after : Objects.requireNonNull(before);
        if (after == null) {
            regions.remove(subject.key());
        } else if (config.loadedScopes().contains(after.scope())) {
            regions.put(after.key(), after);
        } else {
            regions.remove(after.key());
        }
        compileWorld(subject.world());
        if (before != null && !before.scope().equals(subject.scope())) {
            sync.changed(before.scope(), subject.key());
        }
        sync.changed(subject.scope(), subject.key());
        return Result.ok(subject);
    }

    private void loadLocked() {
        Set<GuardScope> scopes = Set.copyOf(config.loadedScopes());
        try {
            List<Region> loaded = store.load(scopes);
            stamp = store.stamp(scopes);
            regions.clear();
            for (Region region : loaded) {
                regions.put(region.key(), region);
            }
            loadFailed = false;
        } catch (RuntimeException e) {
            loadFailed = true;
            warn.accept(
                    "guard: regions could not be loaded, every world is locked until they can be: " + e.getMessage());
        }
        compileAll();
    }

    private void compileAll() {
        Set<String> names = new HashSet<>(worlds.loaded());
        for (String world : names) {
            compileWorld(world);
        }
        for (String world : snapshot.worlds().keySet()) {
            if (!names.contains(world)) {
                swap(snapshot.without(world));
            }
        }
    }

    private void compileWorld(String world) {
        Optional<GuardWorlds.Height> height = worlds.height(world);
        if (height.isEmpty()) {
            return;
        }
        GuardConfig.WorldDefaults defaults = config.world(world);
        GuardSnapshot current = snapshot;
        WorldIndex previous = current.world(world).orElse(null);
        WorldIndex index = Compiler.compile(
                tree,
                new Compiler.Input(
                        world,
                        height.get().minY(),
                        height.get().maxY(),
                        regions(world),
                        defaults.rules(),
                        defaults.behaviours(),
                        loadFailed),
                config.templates(),
                previous,
                compiledTree);
        GuardSnapshot next = current.tree() == tree
                ? current.with(index)
                : new GuardSnapshot(tree, current.worlds(), current.generation()).with(index);
        swap(next);
    }

    private void swap(GuardSnapshot next) {
        snapshot = next;
        compiledTree = next.tree();
        for (Consumer<GuardSnapshot> listener : listeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                warn.accept("guard: a snapshot listener failed: " + e.getMessage());
            }
        }
    }

    private boolean worldLocked(String world) {
        return loadFailed || snapshot.world(world).map(WorldIndex::locked).orElse(false);
    }

    private <T> CompletableFuture<T> run(java.util.function.Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.async(() -> {
            try {
                future.complete(task.get());
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static long distanceSq(Bounds b, int x, int y, int z) {
        long dx = Math.max(0, Math.max((long) b.minX() - x, (long) x - b.maxX()));
        long dy = Math.max(0, Math.max((long) b.minY() - y, (long) y - b.maxY()));
        long dz = Math.max(0, Math.max((long) b.minZ() - z, (long) z - b.maxZ()));
        return dx * dx + dy * dy + dz * dz;
    }
}
