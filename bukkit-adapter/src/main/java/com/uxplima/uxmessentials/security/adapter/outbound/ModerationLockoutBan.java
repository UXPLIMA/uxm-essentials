package com.uxplima.uxmessentials.security.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The moderation-backed {@link LockoutBan}: a verification lockout is issued as an ordinary tempban through the
 * existing {@link TempBan} use case, so it lands in the same ban table, the same history, the same staff broadcast and
 * the same cross-server sync as every other ban, and {@code /unban} lifts it like any other. The security module owns
 * no ban list of its own.
 *
 * <p>The issuer is the server rather than a staff member, which is exactly what {@code actor} being the console ref
 * means here: it shows up in the history as a server-issued sanction, and the duration cap that applies to a human's
 * permission tier does not apply, because the console holds no {@code maxduration} node and no node held means
 * unlimited.
 *
 * <p>The ban is deliberately <em>not</em> silent: staff seeing "the server tempbanned this account" is the point. If
 * an operator would rather it were quiet, the security config can turn the escalation off and the lockout falls back
 * to the in-memory cooldown.
 *
 * <p>Bound only when the moderation module is enabled; with moderation off the wiring binds {@link LockoutBan#NONE}.
 */
@NullMarked
public final class ModerationLockoutBan implements LockoutBan {

    private final TempBan tempBan;
    private final PlayerRef issuer;

    public ModerationLockoutBan(TempBan tempBan, PlayerRef issuer) {
        this.tempBan = Objects.requireNonNull(tempBan, "tempBan");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
    }

    @Override
    public boolean ban(PlayerRef target, Duration duration, String reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(reason, "reason");
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        // A refusal (an exempt target, most likely a staff account) is reported honestly rather than swallowed, so
        // the caller still applies the in-memory lockout instead of believing the account was barred.
        return tempBan.tempban(
                        issuer,
                        target,
                        SanctionDuration.format(duration),
                        reason.isBlank() ? Optional.empty() : Optional.of(reason),
                        false)
                .isOk();
    }
}
