package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ModerationPlaceholders} over the moderation context's read sides: the {@link MutePolicy} the
 * messaging mute gate reads and the {@link JailGate} the teleport jail gate reads (the {@code muted}/
 * {@code jailed} bare keys), the {@link ModerationRepository} ban/mute state (the {@code moderation_ban_*}/
 * {@code moderation_mute_*} family), the {@link Sanctions} freeze read and the warning count. These are the
 * same reads the {@code /msg}, {@code /home}, {@code /checkban}, {@code /checkmute} and {@code /warns} paths
 * consult, so a placeholder agrees with what a sanctioned player actually experiences.
 *
 * <p>Every "active" read is clock-gated with the injected {@link Clock} exactly as {@code CheckBan}/
 * {@code CheckMute} are ({@code state.isActiveAt(clock.instant())}), so an expired tempban or timed mute reads
 * as not banned/muted even before the sweep removes its row. A permanent {@code /ban} is recorded as a tempban
 * with a far-future expiry, so a remaining wait beyond {@link #PERMANENT_THRESHOLD} is rendered as permanent
 * (empty remaining), matching the permanent {@code /mute} shape.
 *
 * <p>When the moderation module is disabled this adapter is never registered; the resolver degrades the
 * boolean keys to "no" and the rest to the dash when the seam is absent.
 */
@NullMarked
public final class GateModerationPlaceholders implements ModerationPlaceholders {

    /** A remaining wait beyond a century is a permanent {@code /ban}'s far-future row, not a real tempban. */
    private static final Duration PERMANENT_THRESHOLD = Duration.ofDays(36_500L);

    private final MutePolicy mutePolicy;
    private final JailGate jailGate;
    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final Clock clock;

    public GateModerationPlaceholders(
            MutePolicy mutePolicy,
            JailGate jailGate,
            ModerationRepository repository,
            Sanctions sanctions,
            Clock clock) {
        this.mutePolicy = Objects.requireNonNull(mutePolicy, "mutePolicy");
        this.jailGate = Objects.requireNonNull(jailGate, "jailGate");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean isMuted(PlayerRef who) {
        return mutePolicy.isMuted(Objects.requireNonNull(who, "who"));
    }

    @Override
    public boolean isJailed(PlayerRef who) {
        return jailGate.isJailed(Objects.requireNonNull(who, "who"));
    }

    @Override
    public boolean isFrozen(PlayerRef who) {
        return sanctions.isFrozen(Objects.requireNonNull(who, "who"));
    }

    @Override
    public int warnCount(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return repository.warns(who, clock.instant()).size();
    }

    @Override
    public Optional<SanctionView> activeBan(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Instant now = clock.instant();
        if (repository.loadTempban(who) instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            return Optional.of(view(banRemaining(now, ban.until()), ban.reason(), ban.issuer()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<SanctionView> activeMute(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Instant now = clock.instant();
        MuteState mute = repository.loadMute(who);
        if (!mute.isActiveAt(now)) {
            return Optional.empty();
        }
        if (mute instanceof MuteState.Timed timed) {
            return Optional.of(
                    view(Optional.of(positive(Duration.between(now, timed.until()))), timed.reason(), timed.issuer()));
        }
        if (mute instanceof MuteState.Permanent permanent) {
            return Optional.of(view(Optional.empty(), permanent.reason(), permanent.issuer()));
        }
        return Optional.empty();
    }

    /** A ban's remaining wait, or empty when its far-future expiry marks it permanent. */
    private static Optional<Duration> banRemaining(Instant now, Instant until) {
        Duration remaining = positive(Duration.between(now, until));
        return remaining.compareTo(PERMANENT_THRESHOLD) >= 0 ? Optional.empty() : Optional.of(remaining);
    }

    private static SanctionView view(Optional<Duration> remaining, Optional<String> reason, Issuer issuer) {
        return new SanctionView(remaining, reason.orElse(PlaceholderResolver.EMPTY), issuer.name());
    }

    private static Duration positive(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
