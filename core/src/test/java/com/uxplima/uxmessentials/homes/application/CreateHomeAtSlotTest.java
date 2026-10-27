package com.uxplima.uxmessentials.homes.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeLimitReached;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

class CreateHomeAtSlotTest {

    private static final int DEFAULT_LIMIT = 2;

    private final InMemoryHomes homes = new InMemoryHomes();
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final Wallet wallet = new Wallet(BigDecimal.ZERO);

    private CreateHomeAtSlot useCase(List<SethomeGuard> guards, DomainGate gate, HomeChargeSettings costs) {
        return new CreateHomeAtSlot(
                homes,
                homes,
                new HomeQuota(permissions, DEFAULT_LIMIT, QuotaReduction.MAX),
                guards,
                notifier.notifier(),
                events,
                gate,
                new HomeCharge(permissions, Optional.of(wallet), costs),
                54,
                CLOCK);
    }

    private CreateHomeAtSlot useCase() {
        return useCase(List.of(), DomainGate.allowAll(), HomeChargeSettings.allFree());
    }

    private static HomeChargeSettings createCosts(String amount) {
        return new HomeChargeSettings(HomeCost.of(new BigDecimal(amount)), HomeCost.free(), HomeCost.free());
    }

    @Test
    void aNewHomeIsSavedAndAnnounced() {
        Result<?, HomeError> result = useCase().create(ALEX, HomeSlot.of(0), at(10, 64, 10));

        assertThat(result.isOk()).isTrue();
        assertThat(homes.findSlot(ALEX, HomeSlot.of(0))).isPresent();
        assertThat(events.published()).hasExactlyElementsOfTypes(HomeCreated.class);
        assertThat(notifier.sent()).singleElement().satisfies(line -> {
            assertThat(line.key()).isEqualTo("home.created");
            assertThat(line.placeholders()).containsEntry("slot", "1");
        });
    }

    @Test
    void theDefaultLimitIsTheSizeOfTheSlotGrid() {
        assertThat(useCase().create(ALEX, HomeSlot.of(1), at(1, 64, 0)).isOk()).isTrue();

        assertThat(useCase().create(ALEX, HomeSlot.of(2), at(2, 64, 0)).asError())
                .contains(HomeError.SLOT_OUT_OF_RANGE);
    }

    @Test
    void aHigherPermissionTierRaisesTheLimit() {
        permissions.quota("uxmessentials.home.limit", QuotaResult.limited(5));

        assertThat(useCase().create(ALEX, HomeSlot.of(4), at(0, 64, 0)).isOk()).isTrue();
    }

    @Test
    void aPlayerWhoLostARankCannotFillAGapBelowTheNewLimit() {
        permissions.quota("uxmessentials.home.limit", QuotaResult.limited(3));
        useCase().create(ALEX, HomeSlot.of(1), at(1, 64, 0));
        useCase().create(ALEX, HomeSlot.of(2), at(2, 64, 0));
        permissions.quota("uxmessentials.home.limit", QuotaResult.limited(2));

        Result<?, HomeError> refused = useCase().create(ALEX, HomeSlot.of(0), at(0, 64, 0));

        assertThat(refused.asError()).contains(HomeError.LIMIT_REACHED);
        assertThat(events.published()).last().isEqualTo(new HomeLimitReached(ALEX, 2, 2));
        assertThat(notifier.sent()).last().satisfies(line -> {
            assertThat(line.key()).isEqualTo("home.limit-reached");
            assertThat(line.placeholders()).containsEntry("limit", "2");
        });
    }

    @Test
    void anUnlimitedPlayerCanUseAnySlotOfTheGrid() {
        permissions.quota("uxmessentials.home.limit", QuotaResult.unlimited());

        assertThat(useCase().create(ALEX, HomeSlot.of(53), at(0, 64, 0)).isOk()).isTrue();
        assertThat(useCase().create(ALEX, HomeSlot.of(54), at(0, 64, 0)).asError())
                .contains(HomeError.SLOT_OUT_OF_RANGE);
    }

    @Test
    void aFailingGuardStopsTheCreateWithItsReason() {
        SethomeGuard noNether = (owner, slot, at) -> Result.err(HomeError.WORLD_DISABLED);

        Result<?, HomeError> result = useCase(List.of(noNether), DomainGate.allowAll(), HomeChargeSettings.allFree())
                .create(ALEX, HomeSlot.of(0), at(0, 64, 0));

        assertThat(result.asError()).contains(HomeError.WORLD_DISABLED);
        assertThat(homes.count(ALEX)).isZero();
        assertThat(notifier.keys())
                .containsExactly(HomeError.WORLD_DISABLED.messageKey().key());
    }

    @Test
    void aVetoKeepsThePlayersMoney() {
        wallet.balance = new BigDecimal("100");

        Result<?, HomeError> result =
                useCase(List.of(), proposal -> false, createCosts("40")).create(ALEX, HomeSlot.of(0), at(0, 64, 0));

        assertThat(result.asError()).contains(HomeError.VETOED);
        assertThat(wallet.balance).isEqualByComparingTo("100");
        assertThat(homes.count(ALEX)).isZero();
    }

    @Test
    void aPaidHomeTakesTheCost() {
        wallet.balance = new BigDecimal("100");

        assertThat(useCase(List.of(), DomainGate.allowAll(), createCosts("40"))
                        .create(ALEX, HomeSlot.of(0), at(0, 64, 0))
                        .isOk())
                .isTrue();
        assertThat(wallet.balance).isEqualByComparingTo("60");
    }

    @Test
    void aPlayerWhoCannotPayGetsNoHome() {
        wallet.balance = new BigDecimal("10");

        Result<?, HomeError> result =
                useCase(List.of(), DomainGate.allowAll(), createCosts("40")).create(ALEX, HomeSlot.of(0), at(0, 64, 0));

        assertThat(result.asError()).contains(HomeError.CANNOT_AFFORD);
        assertThat(homes.count(ALEX)).isZero();
        assertThat(wallet.balance).isEqualByComparingTo("10");
    }

    @Test
    void theCostBypassMakesItFree() {
        permissions.grant(ALEX, "uxmessentials.home.bypass.cost");

        assertThat(useCase(List.of(), DomainGate.allowAll(), createCosts("40"))
                        .create(ALEX, HomeSlot.of(0), at(0, 64, 0))
                        .isOk())
                .isTrue();
        assertThat(wallet.balance).isEqualByComparingTo("0");
    }

    @Test
    void aFreshHomeStartsWithNoLeftoverInvites() {
        homes.addInvite(ALEX, HomeSlot.of(0), UUID.randomUUID());

        useCase().create(ALEX, HomeSlot.of(0), at(0, 64, 0));

        assertThat(homes.invites(ALEX, HomeSlot.of(0))).isEmpty();
    }

    /** A single balance that every player shares, enough to see whether money moved. */
    private static final class Wallet implements HomeEconomy {

        private BigDecimal balance;

        private Wallet(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!canAfford(who, amount, currencyId)) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }
    }
}
