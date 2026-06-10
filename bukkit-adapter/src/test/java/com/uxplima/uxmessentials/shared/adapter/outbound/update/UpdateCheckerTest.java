package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Version;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Coverage of {@link UpdateChecker} with a mocked {@link HttpClient} (never the network). Asserts that a newer
 * release tag is recorded as available, an equal/older tag clears it, and a non-2xx, transport-failure, or
 * unparseable body leaves nothing flagged and never throws — the no-crash, no-block contract.
 */
class UpdateCheckerTest {

    private static final Version CURRENT = new Version(2, 0, 0);

    @Test
    void flagsNewerRelease() {
        UpdateChecker checker = checkerReturning(body(200, "{\"tag_name\":\"v2.1.0\"}"));
        checker.start();
        assertThat(checker.available()).contains(new Version(2, 1, 0));
    }

    @Test
    void doesNotFlagEqualOrOlderRelease() {
        UpdateChecker olderChecker = checkerReturning(body(200, "{\"tag_name\":\"1.9.9\"}"));
        olderChecker.start();
        assertThat(olderChecker.available()).isEmpty();

        UpdateChecker equalChecker = checkerReturning(body(200, "{\"tag_name\":\"2.0.0\"}"));
        equalChecker.start();
        assertThat(equalChecker.available()).isEmpty();
    }

    @Test
    void ignoresNonSuccessStatus() {
        UpdateChecker checker = checkerReturning(body(404, "Not Found"));
        checker.start();
        assertThat(checker.available()).isEmpty();
    }

    @Test
    void ignoresUnparseableBody() {
        UpdateChecker checker = checkerReturning(body(200, "<html>error</html>"));
        checker.start();
        assertThat(checker.available()).isEmpty();
    }

    @Test
    void swallowsTransportFailure() {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), anyStringHandler()))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("boom")));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), noopLog(), CURRENT, onceSettings(), client);
        checker.start();
        assertThat(checker.available()).isEmpty();
    }

    @Test
    void exposesCurrentVersion() {
        UpdateChecker checker = checkerReturning(body(200, "{\"tag_name\":\"2.0.0\"}"));
        assertThat(checker.current()).isEqualTo(CURRENT);
    }

    private static UpdateChecker checkerReturning(HttpResponse<String> response) {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), anyStringHandler()))
                .thenReturn(CompletableFuture.completedFuture(response));
        return new UpdateChecker(new InlineScheduler(), noopLog(), CURRENT, onceSettings(), client);
    }

    private static HttpResponse.BodyHandler<String> anyStringHandler() {
        return ArgumentMatchers.any();
    }

    private static HttpResponse<String> body(int status, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    /** Once-on-enable settings (interval zero) so the inline scheduler does not loop. */
    private static UpdateCheckSettings onceSettings() {
        return new UpdateCheckSettings(true, "https://example.com/latest", true, Duration.ZERO);
    }

    private static Logger noopLog() {
        return new Logger() {
            @Override
            public void info(String m, Object... a) {}

            @Override
            public void warn(String m, Object... a) {}

            @Override
            public void error(String m, Throwable t) {}

            @Override
            public void debug(String m, Object... a) {}
        };
    }

    /** Runs every scheduled task inline; only {@code async}/{@code asyncAfter} are exercised here. */
    private static final class InlineScheduler implements Scheduler {
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
}
