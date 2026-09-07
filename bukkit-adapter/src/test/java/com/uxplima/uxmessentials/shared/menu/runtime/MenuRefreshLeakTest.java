package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.EngineScheduler;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestViewer;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.runtime.MenuRefresh;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

class MenuRefreshLeakTest {

    @Test
    void enabledRefreshSchedulesOneTaskAndCancelOnCloseBalances() {
        var sched = new RecordingScheduler();
        MenuHolder h = holderWithRefresh(true);
        MenuRefresh.start(h, EngineScheduler.of(sched), () -> {});
        assertThat(sched.scheduled).isEqualTo(1);
        h.cancelRefresh();
        assertThat(sched.cancelled).isEqualTo(1);
        assertThat(sched.scheduled).isEqualTo(sched.cancelled);
    }

    @Test
    void cancelIsIdempotentSoBalanceHolds() {
        var sched = new RecordingScheduler();
        MenuHolder h = holderWithRefresh(true);
        MenuRefresh.start(h, EngineScheduler.of(sched), () -> {});
        h.cancelRefresh();
        h.cancelRefresh();
        assertThat(sched.cancelled).isEqualTo(1);
    }

    @Test
    void disabledRefreshSchedulesNothing() {
        var sched = new RecordingScheduler();
        MenuRefresh.start(holderWithRefresh(false), EngineScheduler.of(sched), () -> {});
        assertThat(sched.scheduled).isZero();
    }

    private static MenuHolder holderWithRefresh(boolean enabled) {
        MenuSpec spec = new MenuSpecLoader()
                .parse("rows = 1\nrefresh { enabled = " + enabled + ", interval-ticks = 20 }\nitems {}");
        MenuContext ctx = MenuContext.of(TestViewer.of(UUID.randomUUID(), "P"), null, 0);
        return new MenuHolder("t", spec, ctx);
    }

    static final class RecordingScheduler implements Scheduler {
        int scheduled = 0;
        int cancelled = 0;

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            scheduled++;
            return () -> cancelled++;
        }

        @Override
        public void onGlobal(Runnable t) {}

        @Override
        public void onRegion(Position p, Runnable t) {}

        @Override
        public void onEntity(PlayerRef p, Runnable t) {}

        @Override
        public void async(Runnable t) {}

        @Override
        public void asyncAfter(Duration d, Runnable t) {}
    }
}
