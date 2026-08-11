package com.uxplima.uxmessentials.regions.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;
import com.uxplima.uxmessentials.api.view.UxmRegionFlag;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published region reads: they run on the server's own thread because WorldGuard's container is live state,
 * they assemble a region in one pass, and a world nobody has loaded is an empty answer rather than a fault.
 */
class RegionsQueriesTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final RegionRef SPAWN = new RegionRef(WORLD, "spawn");
    private static final RegionRef ARENA = new RegionRef(WORLD, "arena");

    private FakeRegions regions;
    private ActionDoubles.InlineScheduler scheduler;
    private RegionsQueries queries;

    @BeforeEach
    void setUp() {
        regions = new FakeRegions();
        scheduler = new ActionDoubles.InlineScheduler();
        queries = new RegionsQueries(regions, new ActionDoubles.NamedWorlds().with(WORLD), scheduler);
    }

    @Test
    void aRegionIsAssembledWithItsPriorityRostersAndSetFlagsOnTheServerThread() {
        List<UxmRegion> found = queries.in("world").join();

        assertThat(found)
                .containsExactly(
                        new UxmRegion(
                                "world",
                                "spawn",
                                10,
                                List.of("g:staff"),
                                List.of("00000000-0000-0000-0000-0000000000aa"),
                                List.of(new UxmRegionFlag("pvp", "DENY"))),
                        new UxmRegion("world", "arena", 0, List.of(), List.of(), List.of()));
        assertThat(scheduler.globalCalls()).isOne();
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void oneFlagIsReadableByNameOffTheAssembledRegion() {
        UxmRegion spawn = queries.region("world", "spawn").join().orElseThrow();

        assertThat(spawn.flag("pvp")).contains("DENY");
        assertThat(spawn.flag("greeting")).isEmpty();
    }

    @Test
    void aRegionThatDoesNotExistAndAWorldThatIsNotLoadedAreBothEmpty() {
        assertThat(queries.region("world", "nowhere").join()).isEmpty();
        assertThat(queries.region("nether", "spawn").join()).isEmpty();
        assertThat(queries.in("nether").join()).isEmpty();
    }

    @Test
    void theCoveringSetKeepsWorldGuardsOwnOrderBecauseThatIsWhatDecidesAnOverlap() {
        regions.covering = List.of(SPAWN, ARENA);

        List<UxmRegion> covering =
                queries.at(new UxmLocation("world", 1, 64, 1)).join();

        assertThat(covering).extracting(UxmRegion::id).containsExactly("spawn", "arena");
    }

    @Test
    void aPointInAWorldNobodyHasLoadedCoversNothing() {
        regions.covering = List.of(SPAWN);

        assertThat(queries.at(new UxmLocation("nether", 1, 64, 1)).join()).isEmpty();
    }

    /** The seam as the GUI sees it, holding two regions and whatever the test says covers a point. */
    private static final class FakeRegions implements RegionService {

        private List<RegionRef> covering = List.of();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return world.equals(WORLD) ? List.of(SPAWN, ARENA) : List.of();
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return regionsIn(world).stream().filter(ref -> ref.id().equals(id)).findFirst();
        }

        @Override
        public List<RegionRef> regionsAt(Position position) {
            return covering;
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return region.equals(SPAWN) ? List.of(new FlagValue("pvp", "DENY")) : List.of();
        }

        @Override
        public List<FlagDescriptor> flagDescriptors(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> members(RegionRef region) {
            return region.equals(SPAWN) ? List.of("00000000-0000-0000-0000-0000000000aa") : List.of();
        }

        @Override
        public List<String> owners(RegionRef region) {
            return region.equals(SPAWN) ? List.of("g:staff") : List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return region.equals(SPAWN) ? 10 : 0;
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            throw new UnsupportedOperationException("the published surface never creates a region");
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            throw new UnsupportedOperationException("the published surface never edits a region");
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            throw new UnsupportedOperationException("the published surface never edits a roster");
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            throw new UnsupportedOperationException("the published surface never edits a region");
        }
    }
}
