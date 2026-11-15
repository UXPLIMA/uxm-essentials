package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * Which permissions a warp asks of the player who travels, and that its cost is taken from that player exactly
 * once and only after every permission, lock and password check has passed.
 */
class WarpAccessTest {

    private static final String LOBBY = "lobby";
    private static final WarpName VAULT = WarpName.of("vault");
    private static final String VIP = "server.rank.vip";

    private final MutableClock clock = new MutableClock(NOW);
    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final WarpFakes.Purse purse = new WarpFakes.Purse();
    private final WarpFakes.Moves moves = new WarpFakes.Moves();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakePermissions permissions = new FakePermissions();

    WarpAccessTest() {
        purse.wallet = new BigDecimal("100");
        warps.put(vault(Optional.empty()));
    }

    private static Warp vault(Optional<String> extraPermission) {
        return Warp.create(VAULT, at(5, 64, 5), SAM, NOW, WarpCost.of(new BigDecimal("25"), "gold"), extraPermission)
                .onServer(LOBBY);
    }

    private UseWarp useWarp(Optional<WarpEconomy> economy) {
        return new UseWarp(
                warps,
                new WarpAccess(permissions, economy),
                moves,
                notifier.notifier(),
                position -> true,
                permissions,
                new SteppedScheduler(clock),
                new WarpFakes.Online(),
                WarpHops.local(LOBBY),
                new PaidTeleports(new SteppedScheduler(clock), new RecordingLogger()),
                new RecordingLogger());
    }

    private UseWarp useWarp() {
        return useWarp(Optional.of(purse));
    }

    @Test
    void aPlayerWithoutTheWarpNodeIsRefusedAndNotCharged() {
        Result<Unit, WarpError> used = useWarp().use(ALEX, VAULT);

        assertThat(used.asError()).contains(WarpError.NO_PERMISSION);
        assertThat(purse.charges).isZero();
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aWarpWithAnExtraPermissionNeedsBothNodes() {
        warps.put(vault(Optional.of(VIP)));
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));

        assertThat(useWarp().use(ALEX, VAULT).asError()).contains(WarpError.NO_PERMISSION);
        assertThat(purse.charges).isZero();

        permissions.grant(ALEX, VIP);
        assertThat(useWarp().use(ALEX, VAULT).isOk()).isTrue();
        assertThat(purse.charges).isEqualTo(1);
    }

    @Test
    void aLockedWarpIsRefusedFreeOfChargeUnlessThePlayerMayBypassTheLock() {
        warps.put(vault(Optional.empty()).withLocked(true));
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));

        assertThat(useWarp().use(ALEX, VAULT).asError()).contains(WarpError.LOCKED);
        assertThat(purse.wallet).isEqualByComparingTo("100");

        permissions.grant(ALEX, DestinationPermissions.WARP_BYPASS_LOCK);
        assertThat(useWarp().use(ALEX, VAULT).isOk()).isTrue();
        assertThat(purse.wallet).isEqualByComparingTo("75");
    }

    @Test
    void theRightPasswordOrThePasswordBypassLetsThePlayerThroughForOneCharge() {
        warps.put(vault(Optional.empty()).withPassword(Optional.of("open")));
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));

        assertThat(useWarp().use(ALEX, VAULT).asError()).contains(WarpError.WRONG_PASSWORD);
        assertThat(useWarp().use(ALEX, VAULT, "open").isOk()).isTrue();
        assertThat(purse.charges).isEqualTo(1);

        permissions.grant(ALEX, DestinationPermissions.WARP_BYPASS_PASSWORD);
        assertThat(useWarp().use(ALEX, VAULT).isOk()).isTrue();
        assertThat(purse.charges).isEqualTo(2);
        assertThat(moves.made).containsExactly("Alex->vault", "Alex->vault");
    }

    @Test
    void aPlayerWhoCannotAffordTheWarpStaysPutAndKeepsTheirMoney() {
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));
        purse.wallet = new BigDecimal("24.99");

        Result<Unit, WarpError> used = useWarp().use(ALEX, VAULT);

        assertThat(used.asError()).contains(WarpError.CANNOT_AFFORD);
        assertThat(purse.wallet).isEqualByComparingTo("24.99");
        assertThat(moves.made).isEmpty();
        assertThat(notifier.keys())
                .containsExactly(WarpError.CANNOT_AFFORD.messageKey().key());
    }

    @Test
    void withNoEconomyAPricedWarpIsFree() {
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));

        assertThat(useWarp(Optional.empty()).use(ALEX, VAULT).isOk()).isTrue();

        assertThat(purse.charges).isZero();
        assertThat(moves.made).containsExactly("Alex->vault");
    }

    @Test
    void aStaffSendChargesTheTravellerNotTheStaffMember() {
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));
        List<String> payers = new ArrayList<>();
        WarpEconomy recording = new WarpEconomy() {
            @Override
            public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
                return purse.canAfford(who, amount, currencyId);
            }

            @Override
            public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
                payers.add(who.name());
                return purse.withdraw(who, amount, currencyId);
            }

            @Override
            public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
                return purse.refund(who, amount, currencyId);
            }
        };

        assertThat(useWarp(Optional.of(recording)).useFor(SAM, ALEX, VAULT).isOk())
                .isTrue();

        assertThat(payers).containsExactly("Alex");
        assertThat(moves.made).containsExactly("Alex->vault");
        assertThat(notifier.keys()).contains("warp.sent");
    }

    @Test
    void aStaffMemberCannotSendAPlayerToAWarpThatPlayerMayNotUse() {
        permissions.grant(SAM, WarpAccess.useNode(VAULT));

        assertThat(useWarp().useFor(SAM, ALEX, VAULT).asError()).contains(WarpError.NO_PERMISSION);

        assertThat(purse.charges).isZero();
        assertThat(moves.made).isEmpty();
    }

    @Test
    void anUnknownWarpChargesNothing() {
        assertThat(useWarp().use(ALEX, WarpName.of("nowhere")).asError()).contains(WarpError.NOT_FOUND);

        assertThat(purse.charges).isZero();
    }

    @Test
    void aFreeWarpNeverTouchesTheEconomy() {
        warps.put(Warp.create(VAULT, at(5, 64, 5), SAM, NOW, WarpCost.free(), Optional.empty())
                .onServer(LOBBY));
        permissions.grant(ALEX, WarpAccess.useNode(VAULT));

        assertThat(useWarp().use(ALEX, VAULT).isOk()).isTrue();

        assertThat(purse.charges).isZero();
    }
}
