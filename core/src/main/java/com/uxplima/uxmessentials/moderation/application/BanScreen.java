package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * The disconnect screen a banned player reads, built one way for the kick when the ban lands, the kick on another
 * server of a network and the login refusal, so all three agree. A permanent ban is stored as a timed row that ends
 * far in the future, so it is told apart with {@link SanctionStrength#endOf}: read as a date it would show a time
 * left of hundreds of thousands of days.
 *
 * @param key the permanent or the timed ban screen
 * @param placeholders the reason, and for a timed ban how long is left
 */
record BanScreen(MessageKey key, Map<String, String> placeholders) {

    BanScreen {
        Objects.requireNonNull(key, "key");
        placeholders = Map.copyOf(placeholders);
    }

    /** The screen for {@code ban} as it stands at {@code now}. */
    static BanScreen of(TempbanState.Active ban, Instant now) {
        Objects.requireNonNull(ban, "ban");
        Objects.requireNonNull(now, "now");
        String reason = ban.reason().orElse("");
        Optional<Instant> end = SanctionStrength.endOf(ban);
        if (end.isEmpty()) {
            return new BanScreen(ModerationMessageKey.BAN_KICK, Map.of("reason", reason));
        }
        String left = SanctionDuration.format(Duration.between(now, end.get()));
        return new BanScreen(ModerationMessageKey.TEMPBAN_KICK, Map.of("duration", left, "reason", reason));
    }

    /** The screen rendered in {@code who}'s language. */
    String render(Notifier notifier, PlayerRef who) {
        return notifier.render(who, key, placeholders);
    }

    /** Disconnect {@code target} with this screen; a no-op when they are not connected here. */
    void kick(Sanctions sanctions, Notifier notifier, PlayerRef target) {
        sanctions.kick(target, key, render(notifier, target));
    }
}
