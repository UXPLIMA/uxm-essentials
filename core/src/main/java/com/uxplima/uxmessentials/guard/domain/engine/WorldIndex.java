package com.uxplima.uxmessentials.guard.domain.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import org.jspecify.annotations.Nullable;

/**
 * One world's compiled regions: immutable apart from the column cache, which fills as chunks are asked about.
 *
 * <p>A lookup is a hash probe for the chunk column, an array read for the 16 block section, and for a section
 * that a region border crosses, a containment test against that section's few candidates. Reads take no lock
 * and allocate nothing once the column exists.
 */
public final class WorldIndex {

    /** Regions covering more chunks than this skip the chunk table and are scanned by bounds instead. */
    static final int LARGE_REGION_CHUNKS = 4096;

    private final String world;
    private final int minSection;
    private final int maxSection;
    private final boolean locked;
    private final CompiledRegion[] ordered;
    private final @Nullable CompiledRegion defaults;
    private final Map<RegionId, CompiledRegion> byId;
    private final Map<Long, CompiledRegion[]> smallByChunk;
    private final CompiledRegion[] large;
    private final long[] setActions;
    private final ConcurrentHashMap<Long, Column> columns;
    private final ConcurrentHashMap<List<CompiledRegion>, RegionSet> interned = new ConcurrentHashMap<>();
    private final RegionSet emptySet;
    private final RegionSet wilderness;
    private final Region defaultsRegion;

    WorldIndex(
            String world,
            int minY,
            int maxY,
            boolean locked,
            CompiledRegion[] ordered,
            @Nullable CompiledRegion defaults,
            Map<Long, Column> reusedColumns,
            Region defaultsRegion) {
        this.defaultsRegion = defaultsRegion;
        this.world = world;
        this.minSection = minY >> 4;
        this.maxSection = maxY >> 4;
        this.locked = locked;
        this.ordered = ordered;
        this.defaults = defaults;
        Map<RegionId, CompiledRegion> ids = new HashMap<>();
        Map<Long, List<CompiledRegion>> chunkLists = new HashMap<>();
        List<CompiledRegion> bigOnes = new ArrayList<>();
        int words = 0;
        for (CompiledRegion region : ordered) {
            ids.put(region.region().id(), region);
            words = Math.max(words, region.setActions().length);
            Bounds b = region.region().shape().bounds();
            long chunks = (long) ((b.maxX() >> 4) - (b.minX() >> 4) + 1) * ((b.maxZ() >> 4) - (b.minZ() >> 4) + 1);
            if (region.region().shape() instanceof Shape.Global || chunks > LARGE_REGION_CHUNKS) {
                bigOnes.add(region);
                continue;
            }
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
                for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                    chunkLists
                            .computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>())
                            .add(region);
                }
            }
        }
        if (defaults != null) {
            words = Math.max(words, defaults.setActions().length);
        }
        this.byId = Map.copyOf(ids);
        Map<Long, CompiledRegion[]> small = new HashMap<>(chunkLists.size() * 2);
        chunkLists.forEach((key, list) -> small.put(key, list.toArray(CompiledRegion[]::new)));
        this.smallByChunk = small;
        this.large = bigOnes.toArray(CompiledRegion[]::new);
        this.setActions = new long[words];
        for (CompiledRegion region : ordered) {
            or(setActions, region.setActions());
        }
        if (defaults != null) {
            or(setActions, defaults.setActions());
        }
        this.columns = new ConcurrentHashMap<>(reusedColumns);
        this.emptySet = intern(new CompiledRegion[0]);
        this.wilderness = intern(withDefaults(new CompiledRegion[0]));
    }

    /** The world defaults as a region, compiled or not. */
    Region defaultsRegion() {
        return defaultsRegion;
    }

    /** The compiled world defaults, if any are set. */
    Optional<CompiledRegion> defaults() {
        return Optional.ofNullable(defaults);
    }

    /** True when any region or the world defaults set any rule at all. */
    public boolean anyRule() {
        for (long word : setActions) {
            if (word != 0) {
                return true;
            }
        }
        return false;
    }

    /** True when the world has default rules or behaviours from config. */
    public boolean hasDefaults() {
        return defaults != null;
    }

    /** The world name. */
    public String world() {
        return world;
    }

    /** True when this world's regions could not be loaded and everything is protected. */
    public boolean locked() {
        return locked;
    }

    /** Every stored region of the world, in precedence order. */
    public List<Region> regions() {
        List<Region> out = new ArrayList<>(ordered.length);
        for (CompiledRegion region : ordered) {
            out.add(region.region());
        }
        return List.copyOf(out);
    }

    List<CompiledRegion> regionsCompiled() {
        return List.of(ordered);
    }

    /** One compiled region by id. */
    public Optional<CompiledRegion> region(RegionId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** How many regions the world has. */
    public int size() {
        return ordered.length;
    }

    /** How many chunk columns have been built so far. */
    public int columnCount() {
        return columns.size();
    }

    /** How many distinct region sets exist so far. */
    public int setCount() {
        return interned.size();
    }

    /** True when any region or the world defaults decide {@code action}. */
    public boolean sets(int action) {
        int word = action >>> 6;
        return word < setActions.length && (setActions[word] & (1L << action)) != 0;
    }

    long[] setActions() {
        return setActions;
    }

    /** The decision for {@code subject} doing {@code action} at a block. */
    public Verdict check(int x, int y, int z, int action, GuardSubject subject) {
        if (locked) {
            return Verdict.DENY;
        }
        if (action < 0 || !sets(action)) {
            return Verdict.UNSET;
        }
        return at(x, y, z).decide(action, subject);
    }

    /**
     * The border test: true when an effect at the source block may reach the target block, meaning the target is
     * wilderness or shares a region family with the source. A locked world lets nothing cross.
     */
    public boolean reachable(int sx, int sy, int sz, int tx, int ty, int tz) {
        if (locked) {
            return false;
        }
        return at(tx, ty, tz).reachableFrom(at(sx, sy, sz));
    }

    /** The regions covering a block. */
    public RegionSet at(int x, int y, int z) {
        if ((y >> 4) < minSection || (y >> 4) > maxSection) {
            return outsideHeight(x, y, z);
        }
        long key = chunkKey(x >> 4, z >> 4);
        Column column = columns.get(key);
        if (column == null) {
            // A chunk no region touches answers the defaults alone and keeps no column, so a player exploring the
            // wilderness costs no memory.
            if (wild(key, x >> 4, z >> 4)) {
                return wilderness;
            }
            column = columns.computeIfAbsent(key, k -> buildColumn(x >> 4, z >> 4));
        }
        return column.at(x, y, z, minSection, maxSection, this);
    }

    private boolean wild(long key, int cx, int cz) {
        if (smallByChunk.containsKey(key)) {
            return false;
        }
        int minX = cx << 4;
        int minZ = cz << 4;
        for (CompiledRegion region : large) {
            Bounds b = region.region().shape().bounds();
            if (b.maxX() >= minX && b.minX() <= minX + 15 && b.maxZ() >= minZ && b.minZ() <= minZ + 15) {
                return false;
            }
        }
        return true;
    }

    private RegionSet outsideHeight(int x, int y, int z) {
        List<CompiledRegion> in = new ArrayList<>();
        for (CompiledRegion region : ordered) {
            if (region.region().shape().contains(x, y, z)) {
                in.add(region);
            }
        }
        return intern(withDefaults(in.toArray(CompiledRegion[]::new)));
    }

    /** The chunk columns built so far that the given bounds do not touch, for reuse by the next compile. */
    Map<Long, Column> columnsOutside(Collection<Bounds> changed) {
        Map<Long, Column> keep = new HashMap<>();
        columns.forEach((key, column) -> {
            int cx = (int) (key >> 32);
            int cz = (int) (long) key;
            Bounds chunk = new Bounds(
                    cx << 4, Integer.MIN_VALUE / 2, cz << 4, (cx << 4) + 15, Integer.MAX_VALUE / 2, (cz << 4) + 15);
            for (Bounds bounds : changed) {
                if (bounds.intersects(chunk)) {
                    return;
                }
            }
            keep.put(key, column);
        });
        return keep;
    }

    RegionSet intern(CompiledRegion[] orderedRegions) {
        return interned.computeIfAbsent(List.of(orderedRegions), list -> new RegionSet(orderedRegions));
    }

    RegionSet emptySet() {
        return emptySet;
    }

    private Column buildColumn(int cx, int cz) {
        CompiledRegion[] small = smallByChunk.getOrDefault(chunkKey(cx, cz), new CompiledRegion[0]);
        List<CompiledRegion> candidates = new ArrayList<>(small.length + large.length);
        Bounds chunk =
                new Bounds(cx << 4, minSection << 4, cz << 4, (cx << 4) + 15, (maxSection << 4) + 15, (cz << 4) + 15);
        for (CompiledRegion region : small) {
            candidates.add(region);
        }
        for (CompiledRegion region : large) {
            if (region.region().shape().bounds().intersects(chunk)) {
                candidates.add(region);
            }
        }
        candidates.sort(Precedence.ORDER);
        Cell[] cells = new Cell[maxSection - minSection + 1];
        Map<String, Cell> cellIntern = new HashMap<>();
        for (int sy = minSection; sy <= maxSection; sy++) {
            Bounds section = new Bounds(cx << 4, sy << 4, cz << 4, (cx << 4) + 15, (sy << 4) + 15, (cz << 4) + 15);
            List<CompiledRegion> inSection = new ArrayList<>();
            boolean exact = true;
            for (CompiledRegion region : candidates) {
                Shape shape = region.region().shape();
                if (!shape.bounds().intersects(section)) {
                    continue;
                }
                inSection.add(region);
                if (!shape.fullyContains(section)) {
                    exact = false;
                }
            }
            CompiledRegion[] array = inSection.toArray(CompiledRegion[]::new);
            String cellKey = exact + Arrays.toString(array);
            final boolean isExact = exact;
            cells[sy - minSection] = cellIntern.computeIfAbsent(
                    cellKey, k -> isExact ? Cell.exact(intern(withDefaults(array))) : Cell.mixed(array));
        }
        return new Column(cells);
    }

    /** The given regions plus the world defaults at the end. */
    CompiledRegion[] withDefaults(CompiledRegion[] regions) {
        if (defaults == null) {
            return regions;
        }
        CompiledRegion[] out = Arrays.copyOf(regions, regions.length + 1);
        out[regions.length] = defaults;
        return out;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static void or(long[] into, long[] bits) {
        for (int i = 0; i < bits.length && i < into.length; i++) {
            into[i] |= bits[i];
        }
    }
}
