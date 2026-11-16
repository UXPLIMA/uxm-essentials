package com.uxplima.uxmessentials.homes.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A {@code uxmessentials.home.limit.<world>.<n>} node caps the owner's homes in that world only, beside the limit on
 * all their homes, whether a home is set there or moved there.
 */
class HomeWorldLimitTest {

    private static final WorldRef NETHER =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "World_Nether");
    private static final String NETHER_NODE = DestinationPermissions.HOME_LIMIT + ".world_nether";

    private final InMemoryHomes homes = new InMemoryHomes();
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final HomeQuota quota = new HomeQuota(permissions, 5, QuotaReduction.MAX);
    private final HomeCharge free = new HomeCharge(permissions, Optional.empty(), HomeChargeSettings.allFree());
    private final CreateHomeAtSlot create = new CreateHomeAtSlot(
            homes,
            homes,
            quota,
            List.of(),
            notifier.notifier(),
            new RecordingEvents(),
            DomainGate.allowAll(),
            free,
            54,
            CLOCK,
            Optional.empty());
    private final RelocateHome relocate = new RelocateHome(
            homes,
            quota,
            List.of(),
            notifier.notifier(),
            new RecordingEvents(),
            DomainGate.allowAll(),
            free,
            CLOCK,
            Optional.empty());

    private static Position inNether(double x) {
        return new Position(NETHER, x, 64, 0, 0f, 0f);
    }

    @Test
    void aWorldNodeCapsTheHomesInThatWorldAndLeavesTheOtherWorldsToTheTotal() {
        permissions.holdTier(ALEX, NETHER_NODE, 1);
        assertThat(create.create(ALEX, HomeSlot.of(0), inNether(1)).isOk()).isTrue();

        assertThat(create.create(ALEX, HomeSlot.of(1), inNether(2)).asError()).contains(HomeError.LIMIT_REACHED);

        assertThat(notifier.sent()).last().satisfies(line -> {
            assertThat(line.key()).isEqualTo("home.world-limit-reached");
            assertThat(line.placeholders()).isEqualTo(Map.of("limit", "1", "world", "World_Nether"));
        });
        assertThat(create.create(ALEX, HomeSlot.of(1), at(10, 64, 10)).isOk()).isTrue();
    }

    @Test
    void aLargeWorldNodeDoesNotLiftTheLimitOnAllHomes() {
        permissions.holdTier(ALEX, NETHER_NODE, 50);
        for (int slot = 0; slot < 5; slot++) {
            assertThat(create.create(ALEX, HomeSlot.of(slot), inNether(slot)).isOk())
                    .isTrue();
        }

        assertThat(create.create(ALEX, HomeSlot.of(5), inNether(5)).asError())
                .as("the grid still ends at the limit on all homes")
                .contains(HomeError.SLOT_OUT_OF_RANGE);
    }

    @Test
    void movingAHomeIntoAFullWorldIsRefusedButMovingItInsideThatWorldIsNot() {
        permissions.holdTier(ALEX, NETHER_NODE, 1);
        create.create(ALEX, HomeSlot.of(0), inNether(1));
        create.create(ALEX, HomeSlot.of(1), at(10, 64, 10));

        assertThat(relocate.relocate(ALEX, HomeSlot.of(1), inNether(2)).asError())
                .contains(HomeError.LIMIT_REACHED);
        assertThat(relocate.relocate(ALEX, HomeSlot.of(0), inNether(9)).isOk()).isTrue();
    }
}
