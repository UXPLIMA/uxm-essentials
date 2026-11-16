package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerDataFiles;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerDataFilesAccess;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.PlayerPresence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A login marks its player as joining once their file is free, and a login refused later stops counting. */
class PlayerDataLoginListenerTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    @TempDir
    Path players;

    private final PlayerPresence presence = new PlayerPresence(Clock.systemUTC());
    private final List<String> warnings = new ArrayList<>();

    private PlayerDataLoginListener listener() {
        return new PlayerDataLoginListener(
                new PlayerDataFiles(players, presence),
                presence,
                (viewer, key, values) -> key.key(),
                new Warnings(),
                Duration.ZERO);
    }

    @Test
    void anAllowedLoginMarksThePlayerJoining() {
        PlayerDataLoginListener listener = listener();

        assertThat(listener.admit(SAM)).isNull();
        listener.decided(SAM.uuid(), true);

        assertThat(presence.present(SAM.uuid())).isTrue();
    }

    @Test
    void aLoginRefusedByALaterListenerStopsCounting() {
        PlayerDataLoginListener listener = listener();
        listener.admit(SAM);

        listener.decided(SAM.uuid(), false);

        assertThat(presence.present(SAM.uuid())).isFalse();
    }

    @Test
    void aLoginIsRefusedWhenASaveOfItsFileDoesNotFinishInTime() throws Exception {
        PlayerDataLoginListener listener = listener();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Thread saving = Thread.ofPlatform().daemon().start(() -> {
            ReentrantLock lock = PlayerDataFilesAccess.lockOf(SAM.uuid());
            lock.lock();
            try {
                held.countDown();
                done.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        Object refusal;
        try {
            held.await();
            refusal = listener.admit(SAM);
        } finally {
            done.countDown();
            saving.join();
        }

        assertThat(refusal)
                .as("the login is refused with a message to join again")
                .isNotNull();
        assertThat(presence.present(SAM.uuid())).isFalse();
        assertThat(warnings).singleElement().asString().startsWith("event=player_data_busy");
    }

    private final class Warnings implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
