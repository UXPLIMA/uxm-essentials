package com.uxplima.uxmessentials.teleport.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmBackCause;
import com.uxplima.uxmessentials.api.view.UxmBackPoint;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequest;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequestDirection;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published teleport query: it reports the requests that still stand, it names the player who would actually
 * move, and reading somebody's return point leaves it where it was.
 */
class TeleportQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Instant NOON = Instant.parse("2026-08-09T12:00:00Z");
    private static final Instant SOON = NOON.plusSeconds(60);

    private FakeRequestRegistry requests;
    private FakeBackStore backLocations;

    @BeforeEach
    void setUp() {
        requests = new FakeRequestRegistry();
        backLocations = new FakeBackStore();
    }

    @Test
    void aPendingRequestCarriesBothPlayersAndItsDeadline() {
        requests.put(request(ALICE, BOB, RequestDirection.TO_TARGET));

        UxmTeleportRequest pending = queries().pendingFor(BOB.uuid()).getFirst();

        assertThat(pending.requesterId()).isEqualTo(ALICE.uuid());
        assertThat(pending.requesterName()).isEqualTo("Alice");
        assertThat(pending.targetId()).isEqualTo(BOB.uuid());
        assertThat(pending.targetName()).isEqualTo("Bob");
        assertThat(pending.direction()).isEqualTo(UxmTeleportRequestDirection.TO_TARGET);
        assertThat(pending.expiresAt()).isEqualTo(SOON);
    }

    @Test
    void theDirectionDecidesWhoWouldMove() {
        requests.put(request(ALICE, BOB, RequestDirection.TO_TARGET));
        UxmTeleportRequest tpa = queries().pendingFor(BOB.uuid()).getFirst();

        assertThat(tpa.moverId()).isEqualTo(ALICE.uuid());
        assertThat(tpa.anchorId()).isEqualTo(BOB.uuid());

        requests.clear();
        requests.put(request(ALICE, BOB, RequestDirection.TO_REQUESTER));
        UxmTeleportRequest tpahere = queries().pendingFor(BOB.uuid()).getFirst();

        assertThat(tpahere.moverId())
                .as("a /tpahere moves the player who was asked, not the one who asked")
                .isEqualTo(BOB.uuid());
        assertThat(tpahere.anchorId()).isEqualTo(ALICE.uuid());
    }

    @Test
    void aPlayerWithNothingWaitingOnThemHasAnEmptyList() {
        assertThat(queries().pendingFor(BOB.uuid())).isEmpty();
        assertThat(queries().outgoingFrom(ALICE.uuid())).isEmpty();
    }

    @Test
    void anOutgoingRequestIsFoundByWhoOpenedIt() {
        requests.put(request(ALICE, BOB, RequestDirection.TO_TARGET));

        assertThat(queries().outgoingFrom(ALICE.uuid()))
                .hasValueSatisfying(request -> assertThat(request.targetId()).isEqualTo(BOB.uuid()));
    }

    @Test
    void aReturnPointCarriesWhereTheyWereAndWhatMovedThem() {
        backLocations.put(ALICE, BackLocation.atDeath(position(), NOON));

        UxmBackPoint point = queries().backPoint(ALICE.uuid()).orElseThrow();

        assertThat(point.location().world()).isEqualTo("world");
        assertThat(point.location().x()).isEqualTo(10.5);
        assertThat(point.cause()).isEqualTo(UxmBackCause.DEATH);
        assertThat(point.capturedAt()).isEqualTo(NOON);
    }

    @Test
    void aTeleportCaptureIsReportedAsOne() {
        backLocations.put(ALICE, BackLocation.beforeTeleport(position(), NOON));

        assertThat(queries().backPoint(ALICE.uuid()).orElseThrow().cause()).isEqualTo(UxmBackCause.TELEPORT);
    }

    @Test
    void readingAReturnPointDoesNotSpendIt() {
        backLocations.put(ALICE, BackLocation.beforeTeleport(position(), NOON));

        queries().backPoint(ALICE.uuid());

        assertThat(queries().backPoint(ALICE.uuid()))
                .as("the plugin clears a capture once the player has gone back to it, and a question must not")
                .isPresent();
    }

    private TeleportQueries queries() {
        return new TeleportQueries(
                requests,
                backLocations,
                new QueryDoubles.MapLookup().with(ALICE).with(BOB));
    }

    private static TeleportRequest request(PlayerRef requester, PlayerRef target, RequestDirection direction) {
        return TeleportRequest.open(requester, target, direction, SOON).request();
    }

    private static Position position() {
        return new Position(new WorldRef(UUID.randomUUID(), "world"), 10.5, 64.0, -20.5, 0f, 0f);
    }

    /** Holds the open requests as a list, with the resolve path a query must never take left as a trap. */
    private static final class FakeRequestRegistry implements RequestRegistry {

        private final List<TeleportRequest> open = new ArrayList<>();

        void put(TeleportRequest request) {
            open.add(request);
        }

        void clear() {
            open.clear();
        }

        @Override
        public void store(TeleportRequest request) {
            throw new AssertionError("a query must never open a request");
        }

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return open.stream().filter(request -> request.id().equals(id)).findFirst();
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            return open.stream()
                    .filter(request -> request.target().equals(target))
                    .toList();
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return open.stream()
                    .filter(request -> request.requester().equals(requester))
                    .findFirst();
        }

        @Override
        public void remove(RequestId id) {
            throw new AssertionError("a query must never resolve a request");
        }
    }

    /** Holds one capture, and shouts if a read clears it the way a completed {@code /back} would. */
    private static final class FakeBackStore implements BackLocationStore {

        private @Nullable PlayerRef owner;
        private @Nullable BackLocation location;

        void put(PlayerRef who, BackLocation captured) {
            owner = who;
            location = captured;
        }

        @Override
        public void capture(PlayerRef who, BackLocation captured) {
            throw new AssertionError("a query must never capture a return point");
        }

        @Override
        public Optional<BackLocation> current(PlayerRef who) {
            return who.equals(owner) ? Optional.ofNullable(location) : Optional.empty();
        }

        @Override
        public void clear(PlayerRef who) {
            throw new AssertionError("a query must never spend a return point");
        }
    }
}
