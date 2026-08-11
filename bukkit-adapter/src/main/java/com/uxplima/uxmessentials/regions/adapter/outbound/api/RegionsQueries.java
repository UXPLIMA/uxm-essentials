package com.uxplima.uxmessentials.regions.adapter.outbound.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;
import com.uxplima.uxmessentials.api.view.UxmRegionFlag;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published region reads, over the same {@link RegionService} seam the {@code /regions} GUI reads.
 *
 * <p>Every read runs on the server thread rather than on a worker, because WorldGuard's region container is live
 * server state: the GUI reads it there too. Nothing here touches a database, so the hop is the whole cost.
 *
 * <p>A region is assembled in one pass: its priority, both rosters and its set flags. A caller asking about a spot
 * almost always wants at least one of those, and four round trips through the seam to build what one can would be
 * a worse deal than the extra reads.
 */
@NullMarked
public final class RegionsQueries implements UxmRegionsQuery {

    private final RegionService regions;
    private final WorldLookup worlds;
    private final Scheduler scheduler;

    public RegionsQueries(RegionService regions, WorldLookup worlds, Scheduler scheduler) {
        this.regions = Objects.requireNonNull(regions, "regions");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean available() {
        return regions.available();
    }

    @Override
    public CompletableFuture<List<UxmRegion>> in(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return AsyncQueries.onServer(
                scheduler,
                () -> worlds.findByName(worldName)
                        .map(world -> views(regions.regionsIn(world)))
                        .orElseGet(List::of));
    }

    @Override
    public CompletableFuture<Optional<UxmRegion>> region(String worldName, String id) {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(id, "id");
        return AsyncQueries.onServer(
                scheduler,
                () -> worlds.findByName(worldName)
                        .flatMap(world -> regions.region(world, id))
                        .map(this::view));
    }

    @Override
    public CompletableFuture<List<UxmRegion>> at(UxmLocation where) {
        Objects.requireNonNull(where, "where");
        return AsyncQueries.onServer(
                scheduler,
                () -> ApiValues.position(worlds, where).map(this::coveringViews).orElseGet(List::of));
    }

    private List<UxmRegion> coveringViews(Position position) {
        return views(regions.regionsAt(position));
    }

    private List<UxmRegion> views(List<RegionRef> refs) {
        List<UxmRegion> views = new ArrayList<>(refs.size());
        for (RegionRef ref : refs) {
            views.add(view(ref));
        }
        return List.copyOf(views);
    }

    private UxmRegion view(RegionRef ref) {
        return new UxmRegion(
                worldName(ref.world()),
                ref.id(),
                regions.priority(ref),
                regions.owners(ref),
                regions.members(ref),
                flags(ref));
    }

    private List<UxmRegionFlag> flags(RegionRef ref) {
        List<UxmRegionFlag> flags = new ArrayList<>();
        for (FlagValue flag : regions.flags(ref)) {
            flags.add(new UxmRegionFlag(flag.name(), flag.value()));
        }
        return List.copyOf(flags);
    }

    private static String worldName(WorldRef world) {
        return world.name();
    }
}
