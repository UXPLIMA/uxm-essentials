package com.uxplima.uxmessentials.regions.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * The one outbound seam over WorldGuard's region API. The regions context reasons entirely through this port and
 * the domain value objects it speaks, so no {@code com.sk89q} type ever reaches the core: the real implementation
 * ({@code WorldGuardRegionService}) is the only class that names WorldGuard, bound behind a plugin-present probe,
 * and a {@code NoWorldGuardRegionService} no-op stands in when WorldGuard is absent.
 *
 * <p>{@link #available()} reports whether the bound implementation can actually reach WorldGuard, so the command
 * surface degrades to a "WorldGuard not installed" message rather than opening an empty window. Every read is
 * fail-safe: a query it cannot evaluate (an unknown world, an unregistered region, any reflective failure) answers
 * with an empty result rather than throwing.
 *
 * <p><b>Phase scope.</b> The reads ({@link #regionsIn}, {@link #region}, {@link #flags}, {@link #members},
 * {@link #owners}, {@link #priority}) back the Phase 1 region-list GUI. The mutations ({@link #create},
 * {@link #setFlag}, {@link #applyMemberChange}, {@link #setPriority}) are declared now so the seam is stable for
 * the Phase 2 flag editor and the Phase 3 members/owners+priority editors; Phase 1 wires no caller to them and both
 * implementations throw {@link UnsupportedOperationException} until those phases land.
 */
public interface RegionService {

    /** Whether WorldGuard is present and its region API is reachable; {@code false} degrades every read to empty. */
    boolean available();

    /** Every region defined in {@code world}, in no guaranteed order; empty when the world has none or is unknown. */
    List<RegionRef> regionsIn(WorldRef world);

    /** The region {@code id} in {@code world}, or empty when no region carries that id. */
    Optional<RegionRef> region(WorldRef world, String id);

    /** The region's currently-set flags as rendered name/value pairs; empty when it sets none or is gone. */
    List<FlagValue> flags(RegionRef region);

    /** The region's member identifiers (player names, uuids, and {@code g:group} entries); empty when it has none. */
    List<String> members(RegionRef region);

    /** The region's owner identifiers (player names, uuids, and {@code g:group} entries); empty when it has none. */
    List<String> owners(RegionRef region);

    /** The region's priority (higher wins overlaps); {@code 0} when the region is unknown. */
    int priority(RegionRef region);

    /**
     * Define a new cuboid region spanning {@code min} to {@code max} in {@code world} under {@code id}. Phase 2.
     *
     * @throws UnsupportedOperationException until Phase 2 wires region creation
     */
    RegionRef create(WorldRef world, String id, Position min, Position max);

    /**
     * Set {@code flag} on {@code region} to its carried value. Phase 2.
     *
     * @throws UnsupportedOperationException until Phase 2 wires the flag editor
     */
    void setFlag(RegionRef region, FlagValue flag);

    /**
     * Apply one add/remove of a member or owner to a region's roster. Phase 3.
     *
     * @throws UnsupportedOperationException until Phase 3 wires the members/owners editor
     */
    void applyMemberChange(RegionMemberChange change);

    /**
     * Set {@code region}'s priority. Phase 3.
     *
     * @throws UnsupportedOperationException until Phase 3 wires priority editing
     */
    void setPriority(RegionRef region, int priority);
}
