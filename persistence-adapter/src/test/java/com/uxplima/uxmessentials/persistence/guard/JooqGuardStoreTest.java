package com.uxplima.uxmessentials.persistence.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.guard.application.port.GuardStore;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.HistoryEntry;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.RegionId;
import com.uxplima.uxmessentials.guard.domain.RegionKey;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Shape;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regions, history and denials through the real migrations. */
@EveryBackend
class JooqGuardStoreTest {

    private static final ServerScope LOBBY = ServerScope.server("lobby");
    private static final ServerScope HUB = ServerScope.set("hub");
    private static final RegionKey SPAWN = new RegionKey("world", RegionId.of("spawn"));
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private GuardStore store;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void open(TestDatabase database) {
        DSLContext dsl = database.dsl();
        store = new JooqGuardStore(
                dsl, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC), warnings::add);
    }

    private static Region rich() {
        Map<BehaviourKey, String> behaviours = new EnumMap<>(BehaviourKey.class);
        behaviours.put(BehaviourKey.MESSAGE_ENTER, "<green>Welcome \"home\"");
        behaviours.put(BehaviourKey.FLY, "deny");
        return Region.create(SPAWN, LOBBY, Shape.Polygon.of(new int[] {0, 30, 15}, new int[] {0, 0, 25}, -10, 120))
                .withPriority(7)
                .withParent(RegionId.of("city"))
                .withFamily("farms")
                .withTemplate("spawn")
                .withTags(Set.of("safe", "hub"))
                .withLocked(true)
                .withRule(
                        new Rule(ActionId.of("break"), Target.parse("nonmember").orElseThrow(), Effect.DENY))
                .withRule(new Rule(
                        ActionId.of("use"),
                        Target.parse("cond:%player_level% >= 5").orElseThrow(),
                        Effect.ALLOW))
                .withBehaviours(Behaviours.of(behaviours))
                .withRole(OWNER, RoleName.OWNER)
                .withImportBatch("wg-1");
    }

    @Test
    void aRegionComesBackExactlyAsItWasStored() {
        Region region = rich();
        store.write(null, region, "Staff", "create");

        assertThat(store.load(Set.of(LOBBY))).containsExactly(region);
        assertThat(store.find(LOBBY, SPAWN)).contains(region);
    }

    @Test
    void everyShapeSurvivesTheTrip() {
        List<Shape> shapes = List.of(
                new Shape.Cuboid(Bounds.of(-5, -64, -5, 5, 319, 5)),
                new Shape.Cylinder(0, 0, 10, 6, 0, 50),
                new Shape.Sphere(3, 70, 3, 12),
                Shape.ChunkSet.of(Set.of(Shape.chunkKey(0, 0), Shape.chunkKey(-1, 4)), -64, 319),
                new Shape.Global());
        for (int i = 0; i < shapes.size(); i++) {
            Region region = Region.create(new RegionKey("world", RegionId.of("r" + i)), LOBBY, shapes.get(i));
            store.write(null, region, "Staff", "create");
            assertThat(store.find(LOBBY, region.key())).contains(region);
        }
    }

    @Test
    void aServerLoadsOnlyTheScopesItAsksFor() {
        store.write(null, rich(), "Staff", "create");
        Region shared = Region.create(
                new RegionKey("world", RegionId.of("plaza")), HUB, new Shape.Cuboid(Bounds.of(0, 0, 0, 1, 1, 1)));
        store.write(null, shared, "Staff", "create");

        assertThat(store.load(Set.of(HUB))).containsExactly(shared);
        assertThat(store.load(Set.of(LOBBY, HUB))).hasSize(2);
    }

    @Test
    void historyKeepsBothSidesNewestFirstAndRemovalDeletesTheRow() {
        Region first = rich();
        Region second = first.withPriority(9);
        store.write(null, first, "Staff", "create");
        store.write(first, second, "Staff", "priority");
        store.write(second, null, "Admin", "remove");

        List<HistoryEntry> history = store.history(SPAWN, 0, 10);
        assertThat(history).extracting(HistoryEntry::change).containsExactly("remove", "priority", "create");
        assertThat(history.get(1).before()).isEqualTo(first);
        assertThat(history.get(1).after()).isEqualTo(second);
        assertThat(store.historyEntry(history.get(0).id())).contains(history.get(0));
        assertThat(store.find(LOBBY, SPAWN)).isEmpty();
    }

    @Test
    void theStampMovesWithEveryWrite() {
        long before = store.stamp(Set.of(LOBBY));
        store.write(null, rich(), "Staff", "create");

        assertThat(store.stamp(Set.of(LOBBY))).isNotEqualTo(before);
    }

    @Test
    void denialsAreLoggedReadAndPruned() {
        Instant old = Instant.parse("2029-01-01T00:00:00Z");
        Instant recent = Instant.parse("2030-01-01T00:00:00Z");
        store.logDenials(List.of(
                new GuardStore.Denial(old, Optional.empty(), "Alex", "world", 1, 2, 3, "spawn", "break"),
                new GuardStore.Denial(recent, Optional.empty(), "Sam", "world", 4, 5, 6, "spawn", "place")));

        assertThat(store.denials(new GuardStore.DenialPlayer(Optional.empty(), "Sam"), null, 0, 10))
                .extracting(GuardStore.Denial::action)
                .containsExactly("place");
        store.pruneDenials(Instant.parse("2029-06-01T00:00:00Z"));
        assertThat(store.denials(null, null, 0, 10))
                .extracting(GuardStore.Denial::player)
                .containsExactly("Sam");
    }

    @Test
    void twoAccountsWhoseNamesDifferOnlyInCaseKeepTheirOwnDenialsAndALongBedrockNameIsKeptWhole() {
        UUID steve = UUID.fromString("00000000-0000-0000-0000-00000000005e");
        UUID lowerSteve = UUID.fromString("00000000-0000-0000-0000-00000000005f");
        UUID bedrock = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
        String floodgate = ".Twenty_Char_Gamer_1";
        Instant at = Instant.parse("2030-01-01T00:00:00Z");
        store.logDenials(List.of(
                new GuardStore.Denial(at, Optional.of(steve), "Steve", "world", 1, 2, 3, "spawn", "break"),
                new GuardStore.Denial(at, Optional.of(lowerSteve), "steve", "world", 1, 2, 3, "spawn", "place"),
                new GuardStore.Denial(at, Optional.empty(), "Steve", "world", 1, 2, 3, "spawn", "use"),
                new GuardStore.Denial(at, Optional.of(bedrock), floodgate, "world", 1, 2, 3, "spawn", "break")));

        assertThat(store.denials(new GuardStore.DenialPlayer(Optional.of(steve), "Steve"), null, 0, 10))
                .as("the account's own, and the one logged by name before accounts were kept")
                .extracting(GuardStore.Denial::action)
                .containsExactlyInAnyOrder("break", "use");
        assertThat(store.denials(new GuardStore.DenialPlayer(Optional.of(bedrock), floodgate), null, 0, 10))
                .singleElement()
                .satisfies(denial -> {
                    assertThat(denial.player()).isEqualTo(floodgate);
                    assertThat(denial.playerId()).contains(bedrock);
                });
    }
}
