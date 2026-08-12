package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.invrollback.adapter.inbound.listener.SnapshotCaptureListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.InvrollbackPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link InvrollbackPlaceholders} seam over the capture listener's own record of what it has written this
 * enable. Both reads are map lookups, so a staff HUD showing "last snapshot" costs nothing and never touches the
 * snapshot table.
 */
@NullMarked
public final class ListenerInvrollbackPlaceholders implements InvrollbackPlaceholders {

    private final SnapshotCaptureListener captures;

    public ListenerInvrollbackPlaceholders(SnapshotCaptureListener captures) {
        this.captures = Objects.requireNonNull(captures, "captures");
    }

    @Override
    public Optional<Instant> lastCapture(PlayerRef who) {
        return captures.lastCapture(Objects.requireNonNull(who, "who").uuid()).map(SnapshotCaptureListener.Capture::at);
    }

    @Override
    public Optional<String> lastCause(PlayerRef who) {
        return captures.lastCapture(Objects.requireNonNull(who, "who").uuid())
                .map(capture -> capture.cause().name().toLowerCase(Locale.ROOT));
    }
}
