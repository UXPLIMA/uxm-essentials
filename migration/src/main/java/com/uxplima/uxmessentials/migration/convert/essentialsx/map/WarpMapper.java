package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXLocation;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed EssentialsX warp into a domain {@link Warp} (docs/12-migration §5.1). EssentialsX
 * warps carry no owner, no cost, and no permission gate, so the mapped warp is free, ungated, and
 * attributed to a synthetic importer owner. A warp whose world the server does not know maps to
 * {@link Optional#empty()} and the caller counts it as a skipped record.
 */
@NullMarked
public final class WarpMapper {

    /** The attribution owner for warps imported from a source that records no creator. */
    private static final PlayerRef IMPORTED = new PlayerRef(new java.util.UUID(0L, 0L), "imported");

    private final WorldNameResolver worlds;

    public WarpMapper(WorldNameResolver worlds) {
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    public Optional<ImportedWarp> map(EssXWarp src) {
        Objects.requireNonNull(src, "src");
        return position(src.location())
                .map(pos -> Warp.create(WarpName.of(src.name()), pos, IMPORTED, Instant.EPOCH))
                .map(ImportedWarp::new);
    }

    private Optional<Position> position(EssXLocation location) {
        return worlds.resolve(location.world()).map(world -> toPosition(world, location));
    }

    private static Position toPosition(WorldRef world, EssXLocation src) {
        return new Position(world, src.x(), src.y(), src.z(), src.yaw(), src.pitch());
    }
}
