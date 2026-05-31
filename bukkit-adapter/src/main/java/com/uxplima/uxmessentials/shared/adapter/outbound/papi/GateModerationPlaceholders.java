package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ModerationPlaceholders} over the moderation context's read sides: the {@link MutePolicy} the
 * messaging mute gate reads and the {@link JailGate} the teleport jail gate reads. These are the same
 * soft-couple seams the {@code /msg} and {@code /home} gates consult, so a placeholder agrees with what a
 * muted/jailed player actually experiences.
 *
 * <p>When the moderation module is disabled both seams resolve to their {@code NEVER} binding, so this
 * adapter is only registered when moderation is enabled; the resolver degrades the placeholders to "no"
 * when the seam is absent.
 */
@NullMarked
public final class GateModerationPlaceholders implements ModerationPlaceholders {

    private final MutePolicy mutePolicy;
    private final JailGate jailGate;

    public GateModerationPlaceholders(MutePolicy mutePolicy, JailGate jailGate) {
        this.mutePolicy = Objects.requireNonNull(mutePolicy, "mutePolicy");
        this.jailGate = Objects.requireNonNull(jailGate, "jailGate");
    }

    @Override
    public boolean isMuted(PlayerRef who) {
        return mutePolicy.isMuted(Objects.requireNonNull(who, "who"));
    }

    @Override
    public boolean isJailed(PlayerRef who) {
        return jailGate.isJailed(Objects.requireNonNull(who, "who"));
    }
}
