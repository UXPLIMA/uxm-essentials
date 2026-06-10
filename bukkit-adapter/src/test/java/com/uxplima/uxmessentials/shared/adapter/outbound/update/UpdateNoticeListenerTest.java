package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Version;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * Coverage of {@link UpdateNoticeListener}: an operator joining while an update is flagged is delivered the
 * {@code common.update-available} notice with the current/latest/url placeholders; a non-op is silent; and an op
 * joining while nothing is flagged is silent. The {@link UpdateChecker} is driven through a mocked HTTP client so
 * no network is touched.
 */
class UpdateNoticeListenerTest {

    private static final Version CURRENT = new Version(2, 0, 0);
    private static final String URL = "https://example.com/latest";

    @Test
    void notifiesOperatorWhenUpdateAvailable() {
        UpdateChecker checker = flaggedChecker("{\"tag_name\":\"v2.1.0\"}");
        Messages messages = mock(Messages.class);
        MessageSink sink = mock(MessageSink.class);
        when(messages.resolve(any(), eq(SharedMessageKey.UPDATE_AVAILABLE), any()))
                .thenReturn("rendered");

        UUID uuid = UUID.randomUUID();
        PlayerRef who = new PlayerRef(uuid, "Admin");
        joinEvent(uuid, "Admin", true, new UpdateNoticeListener(checker, URL, messages, sink));

        verify(messages)
                .resolve(
                        who,
                        SharedMessageKey.UPDATE_AVAILABLE,
                        Map.of("current", "2.0.0", "latest", "2.1.0", "url", URL));
        verify(sink).deliver(who, "rendered");
    }

    @Test
    void silentForNonOperator() {
        UpdateChecker checker = flaggedChecker("{\"tag_name\":\"v2.1.0\"}");
        Messages messages = mock(Messages.class);
        MessageSink sink = mock(MessageSink.class);

        joinEvent(UUID.randomUUID(), "Bob", false, new UpdateNoticeListener(checker, URL, messages, sink));

        verify(sink, never()).deliver(any(), any());
    }

    @Test
    void silentWhenNoUpdateFlagged() {
        UpdateChecker checker = flaggedChecker("{\"tag_name\":\"1.0.0\"}");
        Messages messages = mock(Messages.class);
        MessageSink sink = mock(MessageSink.class);

        joinEvent(UUID.randomUUID(), "Admin", true, new UpdateNoticeListener(checker, URL, messages, sink));

        verify(sink, never()).deliver(any(), any());
    }

    private static void joinEvent(UUID uuid, String name, boolean op, UpdateNoticeListener listener) {
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(op);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onJoin(event);
    }

    private static UpdateChecker flaggedChecker(String json) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
        UpdateCheckSettings settings = new UpdateCheckSettings(true, URL, true, Duration.ZERO);
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), noopLog(), CURRENT, settings, client);
        checker.start();
        return checker;
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
