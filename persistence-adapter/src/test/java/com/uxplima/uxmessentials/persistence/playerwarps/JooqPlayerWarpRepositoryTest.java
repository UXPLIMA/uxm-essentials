package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpFavourites.PLAYER_WARP_FAVOURITES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.playerwarps.application.port.RentArchiveCursor;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The player warp queries the rent sweep deletes by, through the real migrations on a SQLite file. */
@EveryBackend
class JooqPlayerWarpRepositoryTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    private DSLContext dsl;
    private JooqPlayerWarpRepository repository;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        repository = new JooqPlayerWarpRepository(dsl);
    }

    private static PlayerWarp warp(String name) {
        return PlayerWarp.create(
                SAM, "Sam", PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), NOW.minus(Duration.ofDays(90)));
    }

    private static PlayerWarp archivedForRent(String name, Instant deadline, Instant lastChange) {
        RentState unpaid = new RentState(
                deadline.minus(Duration.ofDays(3)),
                Optional.of(deadline.minus(Duration.ofDays(3))),
                Optional.of(deadline));
        return warp(name).withRent(unpaid, lastChange).withStatus(WarpStatus.ARCHIVED, lastChange);
    }

    @Test
    void onlyWarpsArchivedForRentAndUntouchedSinceTheCutoffAreDueForHardDelete() {
        Instant old = CUTOFF.minus(Duration.ofDays(1));
        repository.save(archivedForRent("late", CUTOFF, CUTOFF));
        repository.save(archivedForRent("older", old, old));
        repository.save(archivedForRent("recent", NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1))));
        repository.save(archivedForRent("rearchived", old, NOW));
        repository.save(archivedForRent("restored", old, old).withStatus(WarpStatus.ACTIVE, old));
        repository.save(warp("owner-archived").withStatus(WarpStatus.ARCHIVED, old));

        List<String> due = repository.archivedForRent(CUTOFF, Optional.empty(), 10).stream()
                .map(w -> w.name().value())
                .toList();

        assertThat(due).containsExactly("older", "late");
        List<PlayerWarp> first = repository.archivedForRent(CUTOFF, Optional.empty(), 1);
        assertThat(first).extracting(w -> w.name().value()).containsExactly("older");
        RentArchiveCursor after = new RentArchiveCursor(
                first.getFirst().rent().flatMap(RentState::archiveAfter).orElseThrow(),
                first.getFirst().id().orElseThrow());
        assertThat(repository.archivedForRent(CUTOFF, Optional.of(after), 10))
                .extracting(w -> w.name().value())
                .containsExactly("late");
    }

    private void favourite(PlayerWarpId warp) {
        dsl.insertInto(PLAYER_WARP_FAVOURITES)
                .set(PLAYER_WARP_FAVOURITES.PLAYER_UUID, SAM.uuid().toString())
                .set(PLAYER_WARP_FAVOURITES.WARP_ID, warp.value())
                .set(PLAYER_WARP_FAVOURITES.ADDED_AT, NOW.toEpochMilli())
                .execute();
    }

    private int favourites(PlayerWarpId warp) {
        return dsl.fetchCount(PLAYER_WARP_FAVOURITES, PLAYER_WARP_FAVOURITES.WARP_ID.eq(warp.value()));
    }

    @Test
    void theGuardedDeleteRemovesAQualifyingWarpWithItsSideRowsOnlyOnce() {
        Instant old = CUTOFF.minus(Duration.ofDays(1));
        PlayerWarpId id = repository.save(archivedForRent("older", old, old));
        favourite(id);

        assertThat(repository.deleteIfArchivedForRent(id, CUTOFF)).isTrue();
        assertThat(repository.findById(id)).isEmpty();
        assertThat(favourites(id)).isZero();

        assertThat(repository.deleteIfArchivedForRent(id, CUTOFF)).isFalse();
    }

    @Test
    void theGuardedDeleteKeepsAWarpThatChangedOrStillHoldsEarnings() {
        Instant old = CUTOFF.minus(Duration.ofDays(1));
        PlayerWarp archived = archivedForRent("restored", old, old);
        PlayerWarpId restored = repository.save(archived);
        repository.save(archived.withId(restored).withStatus(WarpStatus.ACTIVE, NOW));
        favourite(restored);
        PlayerWarpId rich = repository.save(archivedForRent("rich", old, old));
        dsl.update(PLAYER_WARPS)
                .set(PLAYER_WARPS.EARNED_AMOUNT, new BigDecimal("40"))
                .where(PLAYER_WARPS.ID.eq(rich.value()))
                .execute();

        assertThat(repository.deleteIfArchivedForRent(restored, CUTOFF)).isFalse();
        assertThat(repository.deleteIfArchivedForRent(rich, CUTOFF)).isFalse();

        assertThat(repository.findById(restored)).isPresent();
        assertThat(favourites(restored)).isEqualTo(1);
        assertThat(repository.findById(rich)).isPresent();
    }

    @Test
    void anOwnerKnownOnlyByUuidIsStoredWithoutANameAndNamedOnEveryRead() {
        PlayerRef stranger = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000007"), "");
        String uuid = stranger.uuid().toString();
        PlayerWarp unnamed = PlayerWarp.create(
                new PlayerRef(stranger.uuid(), uuid),
                uuid,
                PlayerWarpName.of("farm"),
                Position.of(WORLD, 0, 64, 0),
                NOW);

        PlayerWarpId id = repository.save(unnamed);

        String stored = dsl.select(PLAYER_WARPS.OWNER_NAME)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id.value()))
                .fetchOne(PLAYER_WARPS.OWNER_NAME);
        assertThat(stored).isNull();
        JooqPlayerWarpRepository named = new JooqPlayerWarpRepository(dsl, owner -> "Stranger");
        assertThat(named.findById(id))
                .hasValueSatisfying(warp -> assertThat(warp.ownerName()).isEqualTo("Stranger"));
    }

    @Test
    void aRealOwnerNameIsKept() {
        PlayerWarpId id = repository.save(warp("shop"));

        assertThat(dsl.select(PLAYER_WARPS.OWNER_NAME)
                        .from(PLAYER_WARPS)
                        .where(PLAYER_WARPS.ID.eq(id.value()))
                        .fetchOne(PLAYER_WARPS.OWNER_NAME))
                .isEqualTo("Sam");
    }
}
