package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.trade.adapter.inbound.gui.CommitGate.Decision;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.Test;

/**
 * A confirmed trade ends exactly one way: settled once both windows are frozen, or both offers handed back when a
 * player left before their window could be frozen, or when the plugin stops first. Never both, never neither, and a
 * trade that was never committed is never taken over.
 */
class CommitGateTest {

    private static CommitGate committed() {
        CommitGate gate = new CommitGate(2);
        gate.arm();
        return gate;
    }

    @Test
    void bothWindowsFrozenSettlesOnce() {
        CommitGate gate = committed();

        List<Decision> decisions = List.of(gate.frozen(), gate.frozen());

        assertThat(decisions).containsExactly(Decision.WAIT, Decision.SETTLE);
        assertThat(gate.takeOverForStop()).isFalse();
    }

    @Test
    void aPlayerWhoLeftBeforeTheFreezeReturnsBothOffersInsteadOfSettling() {
        CommitGate leftFirst = committed();
        CommitGate leftLast = committed();

        List<Decision> first = List.of(leftFirst.retired(), leftFirst.frozen());
        List<Decision> last = List.of(leftLast.frozen(), leftLast.retired());

        assertThat(first).containsExactly(Decision.WAIT, Decision.RETURN);
        assertThat(last).containsExactly(Decision.WAIT, Decision.RETURN);
    }

    @Test
    void bothPlayersLeavingReturnsOnce() {
        CommitGate gate = committed();

        assertThat(List.of(gate.retired(), gate.retired())).containsExactly(Decision.WAIT, Decision.RETURN);
    }

    @Test
    void aStopBeforeTheFreezeTakesTheTradeOverAndALateHopDoesNothing() {
        CommitGate gate = committed();

        assertThat(gate.takeOverForStop()).isTrue();
        assertThat(List.of(gate.frozen(), gate.frozen())).containsExactly(Decision.WAIT, Decision.WAIT);
        assertThat(gate.takeOverForStop()).isFalse();
    }

    @Test
    void aStopAfterOneSideLeftStillReturnsOnlyOnce() {
        CommitGate gate = committed();
        List<Object> endings = new ArrayList<>();

        endings.add(gate.retired());
        endings.add(gate.takeOverForStop());
        endings.add(gate.frozen());

        assertThat(endings).containsExactly(Decision.WAIT, true, Decision.WAIT);
    }

    @Test
    void aStopDuringTheCancelOfATradeThatWasNeverCommittedLeavesTheReturnToTheCancel() {
        // A close or a quit won the trade on its own thread and is returning the offers; the stop runs meanwhile.
        CommitGate gate = new CommitGate(2);

        assertThat(gate.takeOverForStop()).isFalse();
    }

    @Test
    void eachSideIsRecordedOnceWhileTheWindowsAreFrozen() {
        CommitGate gate = committed();

        boolean closingWindow = gate.claimSide(TradeSide.INITIATOR);
        boolean freezeHop = gate.claimSide(TradeSide.INITIATOR);
        boolean otherSide = gate.claimSide(TradeSide.PARTNER);

        assertThat(closingWindow).isTrue();
        assertThat(freezeHop).isFalse();
        assertThat(otherSide).isTrue();
    }

    @Test
    void noSideIsRecordedOutsideTheFreeze() {
        CommitGate idle = new CommitGate(2);
        CommitGate settling = committed();
        settling.frozen();
        settling.frozen();

        assertThat(idle.claimSide(TradeSide.INITIATOR)).isFalse();
        assertThat(settling.claimSide(TradeSide.INITIATOR)).isFalse();
    }
}
