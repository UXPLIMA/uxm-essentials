package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.trade.adapter.inbound.gui.CommitGate.Decision;
import org.junit.jupiter.api.Test;

/**
 * A confirmed trade ends exactly one way: settled once both windows are frozen, or both offers handed back when a
 * player left before their window could be frozen, or when the plugin stops first. Never both, never neither.
 */
class CommitGateTest {

    @Test
    void bothWindowsFrozenSettlesOnce() {
        CommitGate gate = new CommitGate(2);

        List<Decision> decisions = List.of(gate.frozen(), gate.frozen());

        assertThat(decisions).containsExactly(Decision.WAIT, Decision.SETTLE);
        assertThat(gate.takeOverForStop()).isFalse();
    }

    @Test
    void aPlayerWhoLeftBeforeTheFreezeReturnsBothOffersInsteadOfSettling() {
        CommitGate leftFirst = new CommitGate(2);
        CommitGate leftLast = new CommitGate(2);

        List<Decision> first = List.of(leftFirst.retired(), leftFirst.frozen());
        List<Decision> last = List.of(leftLast.frozen(), leftLast.retired());

        assertThat(first).containsExactly(Decision.WAIT, Decision.RETURN);
        assertThat(last).containsExactly(Decision.WAIT, Decision.RETURN);
    }

    @Test
    void bothPlayersLeavingReturnsOnce() {
        CommitGate gate = new CommitGate(2);

        assertThat(List.of(gate.retired(), gate.retired())).containsExactly(Decision.WAIT, Decision.RETURN);
    }

    @Test
    void aStopBeforeTheFreezeTakesTheTradeOverAndALateHopDoesNothing() {
        CommitGate gate = new CommitGate(2);

        assertThat(gate.takeOverForStop()).isTrue();
        assertThat(List.of(gate.frozen(), gate.frozen())).containsExactly(Decision.WAIT, Decision.WAIT);
        assertThat(gate.takeOverForStop()).isFalse();
    }

    @Test
    void aStopAfterOneSideLeftStillReturnsOnlyOnce() {
        CommitGate gate = new CommitGate(2);
        List<Object> endings = new ArrayList<>();

        endings.add(gate.retired());
        endings.add(gate.takeOverForStop());
        endings.add(gate.frozen());

        assertThat(endings).containsExactly(Decision.WAIT, true, Decision.WAIT);
    }
}
