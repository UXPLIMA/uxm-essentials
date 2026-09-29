package com.uxplima.uxmessentials.shared.adapter.outbound.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.api.audit.AuditEntry;
import com.uxplima.uxmessentials.api.audit.AuditFeed;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every line written on the audit channel is also handed to whoever subscribed to the feed.
 *
 * <p>The Discord bridge looked the feed up and found nothing, because nothing published one: a moderation or economy
 * line reached the log and never reached Discord, and the bridge said so on every start.
 */
class AuditChannelTest {

    private final RecordingLogger written = new RecordingLogger();
    private final RecordingLogger failures = new RecordingLogger();
    private final AuditChannel channel = new AuditChannel(failures);

    @Test
    @DisplayName("a line written through the channel reaches a subscriber as its event and its fields")
    void aWrittenLineReachesTheSubscriber() {
        List<AuditEntry> heard = new ArrayList<>();
        channel.subscribe(heard::add);

        channel.tee(written)
                .info(
                        "event=player_mute actor={} target={} ok={} reason={}",
                        "a-uuid",
                        "t-uuid",
                        true,
                        "\"spam in \\\"chat\\\"\"");

        assertThat(written.lines).hasSize(1);
        assertThat(heard).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo("player_mute");
            assertThat(entry.fields())
                    .containsExactly(
                            Map.entry("actor", "a-uuid"),
                            Map.entry("target", "t-uuid"),
                            Map.entry("ok", "true"),
                            Map.entry("reason", "\"spam in \\\"chat\\\"\""));
        });
    }

    @Test
    @DisplayName("a warning line reaches the subscriber too, and a line with no event reaches nobody")
    void onlyAuditLinesArePublished() {
        List<AuditEntry> heard = new ArrayList<>();
        channel.subscribe(heard::add);
        Logger log = channel.tee(written);

        log.warn("event=pay_compensation_failed player={}", "p-uuid");
        log.info("loaded {} warps", 3);

        assertThat(heard).extracting(AuditEntry::event).containsExactly("pay_compensation_failed");
        assertThat(written.lines).hasSize(2);
    }

    @Test
    @DisplayName("a closed subscription hears nothing more, and closing it twice is harmless")
    void aClosedSubscriptionHearsNothing() {
        List<AuditEntry> heard = new ArrayList<>();
        AuditFeed.Subscription open = channel.subscribe(heard::add);
        open.close();
        open.close();

        channel.tee(written).info("event=vault_purge actor=system count={}", 2);

        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("a subscriber that throws neither stops the line being logged nor the next subscriber hearing it")
    void aThrowingSubscriberHurtsNobody() {
        List<AuditEntry> heard = new ArrayList<>();
        channel.subscribe(entry -> {
            throw new IllegalStateException("bridge down");
        });
        channel.subscribe(heard::add);

        assertThatCode(() -> channel.tee(written).info("event=player_kick actor={} target={}", "a", "t"))
                .doesNotThrowAnyException();

        assertThat(written.lines).hasSize(1);
        assertThat(heard).hasSize(1);
        assertThat(failures.lines).singleElement().asString().contains("bridge down");
    }

    private static final class RecordingLogger implements Logger {
        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add(render(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            lines.add(render(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message + " " + cause);
        }

        @Override
        public void debug(String message, Object... args) {
            lines.add(render(message, args));
        }

        private static String render(String message, Object... args) {
            return org.slf4j.helpers.MessageFormatter.arrayFormat(message, args).getMessage();
        }
    }
}
