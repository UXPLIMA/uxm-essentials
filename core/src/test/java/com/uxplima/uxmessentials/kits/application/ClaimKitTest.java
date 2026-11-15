package com.uxplima.uxmessentials.kits.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitDefinitionBuilder;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitFullPolicy;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.kits.domain.KitSchedule;
import com.uxplima.uxmessentials.kits.domain.event.KitClaimed;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkCooldowns;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

class ClaimKitTest {

    private static final KitId STARTER = KitId.of("starter");

    private final MutableClock clock = new MutableClock(NOW);
    private final Kits kits = new Kits();
    private final FakePermissions permissions = new FakePermissions();
    private final Claims claims = new Claims();
    private final Purse purse = new Purse();
    private final Inventory inventory = new Inventory();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();

    private ClaimKit claimKit() {
        KitAccess access = new KitAccess(permissions, new ClockCooldowns(clock), claims, Optional.of(purse));
        return new ClaimKit(
                kits, access, inventory, notifier.notifier(), events, clock, Optional.of(purse), new RecordingLogger());
    }

    /** A claim path on one backend of a network, sharing {@code ledger} with the others, with Alex logging in. */
    private ClaimKit networkClaimKit(MemoryClaimLedger ledger, QueuedScheduler scheduler) {
        NetworkClaims shared = new NetworkClaims(ledger, scheduler, clock, new RecordingLogger());
        shared.joined(ALEX.uuid());
        KitAccess access = new KitAccess(
                permissions,
                new NetworkCooldowns(shared, permissions, clock),
                new NetworkKitClaims(shared),
                Optional.of(purse));
        return new ClaimKit(
                kits, access, inventory, notifier.notifier(), events, clock, Optional.of(purse), new RecordingLogger());
    }

    private void define(KitDefinition kit) {
        kits.save(kit);
    }

    private static KitDefinitionBuilder starter() {
        return KitDefinition.builder().id(STARTER).items(List.of(KitItem.of("STONE_SWORD", 1)));
    }

    @Test
    void aClaimGivesTheItemsAndStartsTheCooldown() {
        define(starter().cooldown(Duration.ofHours(1)).build());
        ClaimKit claim = claimKit();

        assertThat(claim.claim(ALEX, STARTER).join().isOk()).isTrue();
        assertThat(inventory.received).containsExactly("Alex:starter");
        assertThat(events.published()).hasExactlyElementsOfTypes(KitClaimed.class);
        assertThat(notifier.keys()).containsExactly("kit.claimed");

        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.ON_COOLDOWN);
        clock.advance(Duration.ofHours(1));
        assertThat(claim.claim(ALEX, STARTER).join().isOk()).isTrue();
        assertThat(inventory.received).hasSize(2);
    }

    @Test
    void aRefusalOnCooldownSaysHowLongIsLeft() {
        define(starter().cooldown(Duration.ofHours(1)).build());
        ClaimKit claim = claimKit();
        claim.claim(ALEX, STARTER).join();
        clock.advance(Duration.ofMinutes(15));

        claim.claim(ALEX, STARTER).join();

        assertThat(notifier.sent())
                .last()
                .satisfies(line -> assertThat(line.placeholders())
                        .containsEntry("kit", "starter")
                        .containsEntry("time", "45m"));
    }

    @Test
    void anUnknownKitIsNotFound() {
        assertThat(claimKit().claim(ALEX, KitId.of("missing")).join().asError()).contains(KitError.NOT_FOUND);
        assertThat(notifier.sent())
                .singleElement()
                .satisfies(line -> assertThat(line.placeholders()).containsEntry("kit", "missing"));
    }

    @Test
    void aPermissionKitNeedsItsNode() {
        define(starter().permission(true).build());

        assertThat(claimKit().claim(ALEX, STARTER).join().asError()).contains(KitError.NO_PERMISSION);
        permissions.grant(ALEX, ContentPermissions.kit("starter"));
        assertThat(claimKit().claim(ALEX, STARTER).join().isOk()).isTrue();
    }

    @Test
    void aOneTimeKitIsClaimedOnce() {
        define(starter().oneTime(true).build());
        ClaimKit claim = claimKit();

        claim.claim(ALEX, STARTER).join();

        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.ALREADY_CLAIMED);
        assertThat(claim.claim(SAM, STARTER).join().isOk()).isTrue();
    }

    @Test
    void aGroupCooldownShortensTheWaitForItsMembers() {
        define(starter()
                .cooldown(Duration.ofHours(24))
                .permissionCooldowns(Map.of("vip", Duration.ofHours(1)))
                .build());
        permissions.grant(ALEX, ContentPermissions.kitCooldown("vip"));
        ClaimKit claim = claimKit();
        claim.claim(ALEX, STARTER).join();
        claim.claim(SAM, STARTER).join();

        clock.advance(Duration.ofHours(1));

        assertThat(claim.claim(ALEX, STARTER).join().isOk()).isTrue();
        assertThat(claim.claim(SAM, STARTER).join().asError()).contains(KitError.ON_COOLDOWN);
    }

    @Test
    void aPaidKitTakesTheMoneyAndARefusedOneDoesNot() {
        define(starter().cost(KitCost.of(new BigDecimal("50"))).build());
        purse.balance = new BigDecimal("70");
        ClaimKit claim = claimKit();

        assertThat(claim.claim(ALEX, STARTER).join().isOk()).isTrue();
        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.CANNOT_AFFORD);
        assertThat(purse.balance).isEqualByComparingTo("20");
        assertThat(inventory.received).hasSize(1);
    }

    @Test
    void aClaimThatCannotBePaidStartsNoCooldown() {
        define(starter()
                .cooldown(Duration.ofHours(1))
                .cost(KitCost.of(BigDecimal.TEN))
                .build());
        ClaimKit claim = claimKit();

        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.CANNOT_AFFORD);
        purse.balance = BigDecimal.TEN;
        assertThat(claim.claim(ALEX, STARTER).join().isOk()).isTrue();
    }

    @Test
    void claimMoneyIsPaidOut() {
        define(starter().claimMoney(new BigDecimal("25")).build());

        claimKit().claim(ALEX, STARTER).join();

        assertThat(purse.balance).isEqualByComparingTo("25");
    }

    @Test
    void aFullInventoryRefusesWhenTheKitSaysSo() {
        define(starter().onFull(KitFullPolicy.DENY).build());
        inventory.full = true;

        assertThat(claimKit().claim(ALEX, STARTER).join().asError()).contains(KitError.INVENTORY_FULL);
        assertThat(inventory.received).isEmpty();
    }

    @Test
    void aKitOutsideItsScheduleIsUnavailable() {
        // NOW is a Tuesday.
        define(starter()
                .schedule(new KitSchedule(
                        Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()))
                .build());

        assertThat(claimKit().claim(ALEX, STARTER).join().asError()).contains(KitError.UNAVAILABLE);
    }

    @Test
    void anAdminCanGiveAKitToAnotherPlayer() {
        define(starter().build());

        Result<?, KitError> result = claimKit().claimFor(SAM, ALEX, STARTER).join();

        assertThat(result.isOk()).isTrue();
        assertThat(inventory.received).containsExactly("Alex:starter");
    }

    @Test
    void twoServersRacingForAOneTimeKitGrantItOnce() {
        define(starter().oneTime(true).build());
        MemoryClaimLedger ledger = new MemoryClaimLedger();
        QueuedScheduler first = new QueuedScheduler();
        QueuedScheduler second = new QueuedScheduler();
        ClaimKit onFirst = networkClaimKit(ledger, first);
        ClaimKit onSecond = networkClaimKit(ledger, second);
        first.runAll();
        second.runAll();

        CompletableFuture<Result<Unit, KitError>> fromFirst = onFirst.claim(ALEX, STARTER);
        CompletableFuture<Result<Unit, KitError>> fromSecond = onSecond.claim(ALEX, STARTER);
        first.runAll();
        second.runAll();

        assertThat(fromFirst.join().isOk()).isTrue();
        assertThat(fromSecond.join().asError()).contains(KitError.ALREADY_CLAIMED);
        assertThat(inventory.received).containsExactly("Alex:starter");
    }

    @Test
    void aOneTimeKitClaimedBeforeThePlayersClaimsAreReadIsNotGranted() {
        define(starter().oneTime(true).build());
        ClaimKit claim = networkClaimKit(new MemoryClaimLedger(), new QueuedScheduler());

        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.LOADING);
        assertThat(inventory.received).isEmpty();
        assertThat(notifier.keys()).containsExactly("kit.loading");
    }

    @Test
    void aOneTimeClaimThatCannotBePaidIsGivenBack() {
        define(starter().oneTime(true).cost(KitCost.of(BigDecimal.TEN)).build());
        MemoryClaimLedger ledger = new MemoryClaimLedger();
        QueuedScheduler scheduler = new QueuedScheduler();
        ClaimKit claim = networkClaimKit(ledger, scheduler);
        scheduler.runAll();

        CompletableFuture<Result<Unit, KitError>> refused = claim.claim(ALEX, STARTER);
        scheduler.runAll();

        assertThat(refused.join().asError()).contains(KitError.CANNOT_AFFORD);
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isFalse();
    }

    @Test
    void aFaultDuringAOneTimeClaimIsReportedAndLeavesTheKitClaimable() {
        define(starter().oneTime(true).cost(KitCost.of(BigDecimal.TEN)).build());
        purse.broken = true;
        RecordingLogger log = new RecordingLogger();
        KitAccess access = new KitAccess(permissions, new ClockCooldowns(clock), claims, Optional.of(purse));
        ClaimKit claim =
                new ClaimKit(kits, access, inventory, notifier.notifier(), events, clock, Optional.of(purse), log);

        assertThat(claim.claim(ALEX, STARTER).join().asError()).contains(KitError.FAILED);
        assertThat(notifier.keys()).containsExactly("command.error");
        assertThat(log.errors()).hasSize(1);
        assertThat(claims.hasClaimed(ALEX, STARTER)).isFalse();
    }

    @Test
    void aFaultAfterTheSharedClaimWasTakenGivesItBack() {
        define(starter().oneTime(true).cost(KitCost.of(BigDecimal.TEN)).build());
        purse.broken = true;
        MemoryClaimLedger ledger = new MemoryClaimLedger();
        QueuedScheduler scheduler = new QueuedScheduler();
        ClaimKit claim = networkClaimKit(ledger, scheduler);
        scheduler.runAll();

        CompletableFuture<Result<Unit, KitError>> failed = claim.claim(ALEX, STARTER);
        scheduler.runAll();

        assertThat(failed.join().asError()).contains(KitError.FAILED);
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isFalse();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheSharedClaimAnswersGetsNothingAndTheClaimGoesBack() {
        define(starter().oneTime(true).build());
        MemoryClaimLedger ledger = new MemoryClaimLedger();
        QueuedScheduler scheduler = new QueuedScheduler();
        ClaimKit claim = networkClaimKit(ledger, scheduler);
        scheduler.runAll();

        CompletableFuture<Result<Unit, KitError>> abandoned = claim.claim(ALEX, STARTER);
        scheduler.leave(ALEX);
        scheduler.runAll();

        assertThat(abandoned.join().asError()).contains(KitError.PLAYER_LEFT);
        assertThat(inventory.received).isEmpty();
        assertThat(ledger.hasKit(ALEX.uuid(), "starter")).isFalse();
    }

    private static final class Kits implements KitRepository {

        private final Map<KitId, KitDefinition> byId = new HashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean exists(KitId id) {
            return byId.containsKey(id);
        }

        @Override
        public void save(KitDefinition definition) {
            byId.put(definition.id(), definition);
        }

        @Override
        public void delete(KitId id) {
            byId.remove(id);
        }
    }

    private static final class Claims implements KitClaimStore {

        private final Set<String> claimed = new HashSet<>();

        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return claimed.contains(who.uuid() + kit.value());
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {
            claimed.add(who.uuid() + kit.value());
        }

        @Override
        public void reset(PlayerRef who, KitId kit) {
            claimed.remove(who.uuid() + kit.value());
        }

        @Override
        public void resetAll(PlayerRef who) {
            claimed.removeIf(key -> key.startsWith(who.uuid().toString()));
        }
    }

    private static final class Purse implements KitEconomy {

        private BigDecimal balance = BigDecimal.ZERO;
        private boolean broken;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (broken) {
                throw new IllegalStateException("the economy is unreachable");
            }
            if (!canAfford(who, amount, currencyId)) {
                return false;
            }
            balance = balance.subtract(amount);
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            balance = balance.add(amount);
            return true;
        }
    }

    private static final class Inventory implements KitGranter {

        private final List<String> received = new ArrayList<>();
        private boolean full;

        @Override
        public boolean hasRoomFor(PlayerRef recipient, KitDefinition kit) {
            return !full;
        }

        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            received.add(recipient.name() + ":" + kit.id().value());
            return Grant.complete();
        }
    }
}
