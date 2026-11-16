package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The random browse order, dealt per round, as a browse sees it. */
@EveryBackend
class JooqPlayerWarpShuffleTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final List<String> CREATED =
            List.of("w01", "w02", "w03", "w04", "w05", "w06", "w07", "w08", "w09", "w10", "w11", "w12");

    private JooqPlayerWarpShuffle shuffle;
    private JooqPlayerWarpBrowse browse;

    @BeforeEach
    void open(TestDatabase database) {
        // Every row starts at the same place, as the rows carried over from the first player warp table do.
        JooqPlayerWarpRepository repository = new JooqPlayerWarpRepository(database.dsl(), UUID::toString, () -> 0L);
        for (String name : CREATED) {
            repository.save(PlayerWarp.create(SAM, "Sam", PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), NOW));
        }
        // A small chunk, so the twelve warps are dealt over several chunks.
        shuffle = new JooqPlayerWarpShuffle(database.dsl(), 5);
        browse = new JooqPlayerWarpBrowse(database.dsl(), Clock.fixed(NOW, ZoneOffset.UTC), UUID::toString);
    }

    private List<String> randomOrder() {
        WarpQuery query = new WarpQuery(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                WarpSort.RANDOM,
                0,
                WarpQuery.MAX_PAGE_SIZE,
                SAM.uuid(),
                Optional.empty());
        return browse.page(query).items().stream().map(WarpCard::name).toList();
    }

    @Test
    void warpsThatShareOnePlaceAreSpreadByTheFirstRound() {
        assertThat(randomOrder()).containsExactlyElementsOf(CREATED);

        int moved = shuffle.shuffle(7);

        assertThat(moved).isEqualTo(CREATED.size());
        assertThat(randomOrder()).containsExactlyInAnyOrderElementsOf(CREATED).isNotEqualTo(CREATED);
    }

    @Test
    void aRoundAlreadyInPlaceWritesNothing() {
        shuffle.shuffle(7);
        List<String> dealt = randomOrder();

        int moved = shuffle.shuffle(7);

        assertThat(moved).isZero();
        assertThat(randomOrder()).containsExactlyElementsOf(dealt);
    }

    @Test
    void eachRoundDealsItsOwnOrderAndTheSameRoundAlwaysDealsTheSameOne() {
        shuffle.shuffle(7);
        List<String> seventh = randomOrder();

        shuffle.shuffle(8);
        List<String> eighth = randomOrder();
        shuffle.shuffle(7);

        assertThat(eighth).containsExactlyInAnyOrderElementsOf(CREATED).isNotEqualTo(seventh);
        assertThat(randomOrder()).containsExactlyElementsOf(seventh);
    }
}
