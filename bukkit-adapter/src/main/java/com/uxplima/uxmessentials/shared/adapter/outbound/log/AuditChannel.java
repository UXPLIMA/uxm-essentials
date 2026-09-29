package com.uxplima.uxmessentials.shared.adapter.outbound.log;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.api.audit.AuditEntry;
import com.uxplima.uxmessentials.api.audit.AuditFeed;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * The audit channel, and the feed of what is written on it.
 *
 * <p>Every module that writes an audit line takes its logger from here, so every line reaches both the channel an
 * operator routes to a retained file and the feed the Discord bridge subscribes to (docs/09-deployment.md, the audit
 * logging section). Five modules used to open the channel straight off SLF4J, the bridge had nothing to subscribe to,
 * and nothing it was built to mirror ever reached Discord.
 *
 * <p>A line is published only while somebody listens, so a server without the bridge pays one empty check per line.
 */
public final class AuditChannel implements AuditFeed {

    private static final String NAME = "com.uxplima.uxmessentials.audit";
    private static final String EVENT = "event=";

    private final List<Registration> listeners = new CopyOnWriteArrayList<>();
    private final Logger failures;

    /** @param failures where a listener that throws is reported, which is the plugin log, never the channel */
    public AuditChannel(Logger failures) {
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /** The audit channel's own logger. Every line it writes is also published. */
    public Logger logger() {
        return tee(new Slf4jLogger(LoggerFactory.getLogger(NAME)));
    }

    /**
     * A logger that writes to {@code target} and publishes each audit line as well. The economy writes its lines to
     * the plugin log rather than the channel, and reaches the feed through this.
     */
    public Logger tee(Logger target) {
        return new Tee(Objects.requireNonNull(target, "target"));
    }

    @Override
    public Subscription subscribe(Consumer<AuditEntry> listener) {
        Registration registration = new Registration(Objects.requireNonNull(listener, "listener"));
        listeners.add(registration);
        return () -> listeners.remove(registration);
    }

    private void publish(String message, Object... args) {
        if (listeners.isEmpty()) {
            return;
        }
        Optional<AuditEntry> entry =
                parse(MessageFormatter.arrayFormat(message, args).getMessage());
        if (entry.isEmpty()) {
            return;
        }
        for (Registration registration : listeners) {
            try {
                registration.listener().accept(entry.get());
            } catch (RuntimeException failure) {
                failures.warn(
                        "event=audit_listener_failed audit_event={} error={}",
                        entry.get().event(),
                        failure.toString());
            }
        }
    }

    /**
     * Split one line into its event and fields. A value that opens with a double quote runs to the matching unescaped
     * quote, so a reason with spaces in it stays one field. A word with no {@code =} belongs to the value before it.
     */
    static Optional<AuditEntry> parse(String line) {
        if (!line.startsWith(EVENT)) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        @Nullable String event = null;
        @Nullable String lastKey = null;
        int i = 0;
        int n = line.length();
        while (i < n) {
            while (i < n && line.charAt(i) == ' ') {
                i++;
            }
            int start = i;
            boolean quoted = false;
            boolean escaped = false;
            while (i < n && (quoted || line.charAt(i) != ' ')) {
                char c = line.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = !quoted;
                }
                i++;
            }
            if (start == i) {
                break;
            }
            String word = line.substring(start, i);
            int equals = word.indexOf('=');
            if (equals <= 0) {
                if (lastKey != null) {
                    fields.merge(lastKey, word, (was, more) -> was + " " + more);
                }
                continue;
            }
            String key = word.substring(0, equals);
            String value = word.substring(equals + 1);
            if (event == null) {
                event = value;
                continue;
            }
            fields.put(key, value);
            lastKey = key;
        }
        return event == null || event.isEmpty() ? Optional.empty() : Optional.of(new AuditEntry(event, fields));
    }

    /** One subscription. A class rather than a record: two subscriptions of one listener are two, and close apart. */
    private static final class Registration {
        private final Consumer<AuditEntry> listener;

        private Registration(Consumer<AuditEntry> listener) {
            this.listener = listener;
        }

        Consumer<AuditEntry> listener() {
            return listener;
        }
    }

    private final class Tee implements Logger {
        private final Logger target;

        private Tee(Logger target) {
            this.target = target;
        }

        @Override
        public void info(String message, Object... args) {
            target.info(message, args);
            publish(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            target.warn(message, args);
            publish(message, args);
        }

        @Override
        public void error(String message, Throwable cause) {
            target.error(message, cause);
        }

        @Override
        public void debug(String message, Object... args) {
            target.debug(message, args);
        }
    }
}
