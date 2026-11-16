package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A player's homes through the real migrations: kept by slot, moved in place, and gone once deleted. */
@EveryBackend
class JooqHomeRepositoryTest {

    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private JooqHomeRepository homes;

    @BeforeEach
    void open(TestDatabase database) {
        homes = new JooqHomeRepository(database.dsl());
    }

    @Test
    void homesAreReadBackBySlotWithTheirExactSpot() {
        homes.save(Home.create(ALEX, HomeSlot.of(2), at(10.5, 64, -3.25, 90f), "survival", NOW));
        homes.save(Home.create(ALEX, HomeSlot.of(0), at(0, 70, 0, 0f), "survival", NOW));
        homes.save(Home.create(SAM, HomeSlot.of(0), at(1, 1, 1, 0f), "survival", NOW));

        assertThat(homes.load(ALEX).all())
                .extracting(home -> home.slot().index())
                .containsExactly(0, 2);
        assertThat(homes.count(ALEX)).isEqualTo(2);
        Home far = homes.findSlot(ALEX, HomeSlot.of(2)).orElseThrow();
        assertThat(far.location()).isEqualTo(at(10.5, 64, -3.25, 90f));
        assertThat(far.knownServer()).contains("survival");
    }

    @Test
    void aSecondSaveOfASlotMovesTheHomeInsteadOfAddingOne() {
        Home first = Home.create(ALEX, HomeSlot.of(0), at(0, 70, 0, 0f), "survival", NOW);
        homes.save(first);

        homes.save(first.relocatedTo(at(5, 80, 5, 0f), Optional.of("lobby"), NOW.plusSeconds(60)));

        assertThat(homes.count(ALEX)).isEqualTo(1);
        Home moved = homes.findSlot(ALEX, HomeSlot.of(0)).orElseThrow();
        assertThat(moved.location()).isEqualTo(at(5, 80, 5, 0f));
        assertThat(moved.knownServer()).contains("lobby");
    }

    @Test
    void aDeletedSlotIsGoneAndDeletingAllLeavesOtherPlayersAlone() {
        homes.save(Home.create(ALEX, HomeSlot.of(0), at(0, 70, 0, 0f), "survival", NOW));
        homes.save(Home.create(ALEX, HomeSlot.of(1), at(0, 70, 0, 0f), "survival", NOW));
        homes.save(Home.create(SAM, HomeSlot.of(0), at(0, 70, 0, 0f), "survival", NOW));

        homes.deleteSlot(ALEX, HomeSlot.of(0));
        assertThat(homes.findSlot(ALEX, HomeSlot.of(0))).isEmpty();
        assertThat(homes.count(ALEX)).isEqualTo(1);

        homes.deleteAll(ALEX);
        assertThat(homes.load(ALEX).all()).isEmpty();
        assertThat(homes.count(SAM)).isEqualTo(1);
    }

    private static Position at(double x, double y, double z, float yaw) {
        return new Position(WORLD, x, y, z, yaw, 0f);
    }
}
