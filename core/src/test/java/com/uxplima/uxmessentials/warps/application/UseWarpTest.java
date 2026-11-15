package com.uxplima.uxmessentials.warps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * A use of a paid warp on this server: every check the use can fail runs before the money moves, so a refused use
 * leaves the wallet as it was. The teleport rules are the real engine's.
 */
class UseWarpTest {

    private static final String LOBBY = "lobby";
    private static final WarpName SHOP = WarpName.of("shop");
    private static final BigDecimal START = new BigDecimal("100");

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final WarpFakes.Warps warps = new WarpFakes.Warps();
    private final WarpFakes.Purse purse = new WarpFakes.Purse();
    private final WarpFakes.Moves moves = new WarpFakes.Moves();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakePermissions permissions = new FakePermissions().grant(ALEX, SHOP.useNode());
    private boolean tagged;
    private boolean unsafe;

    UseWarpTest() {
        warps.put(shop());
        purse.wallet = START;
        moves.engine = WarpFakes.engine(new ClockCooldowns(clock), notifier, () -> tagged);
    }

    private static Warp shop() {
        return Warp.create(SHOP, at(0, 64, 0), SAM, NOW, WarpCost.of(new BigDecimal("10"), "gold"), Optional.empty())
                .onServer(LOBBY);
    }

    private UseWarp useWarp() {
        return new UseWarp(
                warps,
                new WarpAccess(permissions, Optional.of(purse)),
                moves,
                notifier.notifier(),
                position -> !unsafe,
                permissions,
                scheduler,
                WarpHops.local(LOBBY));
    }

    @Test
    void aPaidUseChargesOnceAndTeleports() {
        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.isOk()).isTrue();
        assertThat(purse.wallet).isEqualByComparingTo("90");
        assertThat(moves.made).containsExactly("Alex->shop");
    }

    @Test
    void aWrongPasswordOnAPaidWarpTakesNoMoney() {
        warps.put(shop().withPassword(Optional.of("secret")));

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP, "guess");

        assertThat(used.asError()).contains(WarpError.WRONG_PASSWORD);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
    }

    @Test
    void anUnsafeDestinationOnAPaidWarpTakesNoMoney() {
        unsafe = true;

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.asError()).contains(WarpError.UNSAFE_LOCATION);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
    }

    @Test
    void aUseTheTeleportRulesRefuseTakesNoMoney() {
        tagged = true;

        Result<Unit, WarpError> used = useWarp().use(ALEX, SHOP);

        assertThat(used.asError()).contains(WarpError.TELEPORT_REFUSED);
        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(moves.made).isEmpty();
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged");
    }

    @Test
    void aStaffSendTheTeleportRulesRefuseTellsTheStaffMemberAndTakesNoMoney() {
        tagged = true;

        useWarp().useFor(SAM, ALEX, SHOP);

        assertThat(purse.wallet).isEqualByComparingTo(START);
        assertThat(notifier.keys()).containsExactly("teleport.combat-tagged", "warp.sent-refused");
    }
}
