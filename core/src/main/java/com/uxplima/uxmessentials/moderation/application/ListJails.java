package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /jails}: list the named jails an operator configured in {@code moderation.conf}. A read-only query
 * against the config seam — without it an operator has to open the file by hand to learn which names
 * {@code /jail <player> <jail>} will accept. Renders a header with the count then one entry per name, or an
 * empty notice when no jails are configured. The directory hands the names back sorted.
 */
public final class ListJails {

    private final JailDirectory directory;
    private final ModerationNotifier notifier;

    public ListJails(JailDirectory directory, ModerationNotifier notifier) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the configured jail names to {@code actor}, sorted, or the empty notice when none exist. */
    public void list(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<String> names = directory.names();
        if (names.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.JAILS_EMPTY);
            return;
        }
        notifier.send(actor, ModerationMessageKey.JAILS_HEADER, Map.of("count", Integer.toString(names.size())));
        names.forEach(name -> notifier.send(actor, ModerationMessageKey.JAILS_ENTRY, Map.of("jail", name)));
    }
}
