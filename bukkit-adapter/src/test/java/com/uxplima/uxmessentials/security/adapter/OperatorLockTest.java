package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.security.domain.OperatorList;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The server's own {@code /op} finds its player by name in any case. On an offline-mode server, where every spelling
 * is its own account, an {@code /op} whose name only an online player of another spelling has would make that player
 * the operator, so it is refused with a line naming both spellings.
 */
class OperatorLockTest {

    private final List<String> told = new ArrayList<>();
    private NameCase nameCase = NameCase.SENSITIVE;
    private ServerMock server;

    @BeforeEach
    void mock() {
        server = MockBukkit.mock();
        server.addPlayer("aDMIN");
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void offlineModeAnOpOfANameOnlyAnotherSpellingHasOnlineIsRefused() {
        OperatorLock lock = lock(OperatorList.of(false, List.of()));

        assertThat(lock.refuses(server.getConsoleSender(), "/op Admin")).isTrue();
        assertThat(told).containsExactly(SecurityMessageKey.SECURITY_OPERATORS_OTHER_SPELLING.key() + " Admin aDMIN");
    }

    @Test
    void offlineModeAnOpSpelledAsTheOnlinePlayerIsLeftToTheList() {
        assertThat(lock(OperatorList.of(false, List.of())).refuses(server.getConsoleSender(), "/op aDMIN"))
                .isFalse();
        assertThat(lock(OperatorList.of(true, List.of("Admin"))).refuses(server.getConsoleSender(), "/op aDMIN"))
                .isTrue();
        assertThat(told).containsExactly(SecurityMessageKey.SECURITY_OPERATORS_REFUSED.key() + " aDMIN -");
    }

    @Test
    void withAuthenticatedLoginsAnyCaseNamesTheOneAccount() {
        nameCase = NameCase.INSENSITIVE;

        assertThat(lock(OperatorList.of(false, List.of())).refuses(server.getConsoleSender(), "/op Admin"))
                .isFalse();
        assertThat(told).isEmpty();
    }

    private OperatorLock lock(OperatorList list) {
        return new OperatorLock(
                server,
                new AtOnce(),
                (viewer, key, placeholders) ->
                        key.key() + " " + placeholders.get("player") + " " + placeholders.getOrDefault("online", "-"),
                (viewer, text) -> told.add(text),
                new Quiet(),
                () -> nameCase,
                list);
    }

    private static final class AtOnce implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class Quiet implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
