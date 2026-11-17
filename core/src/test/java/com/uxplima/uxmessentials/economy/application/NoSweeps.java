package com.uxplima.uxmessentials.economy.application;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** A sweep journal for tests that never sweep: it holds nothing and opens nothing. */
final class NoSweeps implements AccountSweep {

    static final AccountSweep JOURNAL = new NoSweeps();

    private NoSweeps() {}

    @Override
    public Opening open(
            SweepKind kind,
            SweepScope scope,
            SweepMode mode,
            Money amount,
            EconomyReason reason,
            PlayerRef actor,
            long at) {
        throw new UnsupportedOperationException("no sweep in this test");
    }

    @Override
    public Step advance(Sweep sweep) {
        throw new UnsupportedOperationException("no sweep in this test");
    }

    @Override
    public void settle(Sweep sweep, SweepOutcome done) {}

    @Override
    public void pause(Sweep sweep, String error) {}

    @Override
    public List<Sweep> resumable() {
        return List.of();
    }

    @Override
    public List<SweepStatus> unfinished() {
        return List.of();
    }

    @Override
    public Optional<SweepStatus> cancel(String id, long at) {
        return Optional.empty();
    }
}
