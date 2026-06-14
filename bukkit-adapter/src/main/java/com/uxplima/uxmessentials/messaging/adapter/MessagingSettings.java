package com.uxplima.uxmessentials.messaging.adapter;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The messaging context's typed config, read once from the module's scoped {@code modules.messaging} subtree
 * at wiring time. It bounds the reply-TTL window (how long a {@code /reply} resolves to the last partner),
 * the mail retention window (after which the expiry sweep deletes a piece of mail), and the sweep interval.
 * Each reads against a sane default so an operator who declares nothing still gets working behaviour.
 *
 * <p>A non-positive reply-TTL or retention is honoured by the domain as "never expires", so an operator can
 * switch either bound off by setting it to zero. The {@code offline-to-mail} flag governs the {@code /msg}
 * offline fallback: with it on, a message to a genuinely-offline player is stored as durable mail instead of
 * rejecting; with it off the not-online rejection stands.
 */
@NullMarked
public final class MessagingSettings {

    private static final long DEFAULT_REPLY_TTL_SECONDS = 300L;
    private static final long DEFAULT_MAIL_RETENTION_DAYS = 30L;
    private static final long DEFAULT_SWEEP_MINUTES = 30L;
    private static final boolean DEFAULT_OFFLINE_TO_MAIL = true;

    private final Duration replyTtl;
    private final Duration mailRetention;
    private final Duration sweepInterval;
    private final boolean offlineToMail;

    public MessagingSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.replyTtl = Duration.ofSeconds(config.getLong("reply-ttl-seconds", DEFAULT_REPLY_TTL_SECONDS));
        this.mailRetention = Duration.ofDays(config.getLong("mail-retention-days", DEFAULT_MAIL_RETENTION_DAYS));
        this.sweepInterval = Duration.ofMinutes(config.getLong("mail-sweep-minutes", DEFAULT_SWEEP_MINUTES));
        this.offlineToMail = config.getBoolean("offline-to-mail", DEFAULT_OFFLINE_TO_MAIL);
    }

    /** How long after the last touch a {@code /reply} still resolves to the same partner. */
    public Duration replyTtl() {
        return replyTtl;
    }

    /** How long a piece of mail is kept before the expiry sweep deletes it. */
    public Duration mailRetention() {
        return mailRetention;
    }

    /** How often the mail-expiry sweep runs. */
    public Duration sweepInterval() {
        return sweepInterval;
    }

    /** Whether a {@code /msg} to a genuinely-offline player is stored as mail instead of being rejected. */
    public boolean offlineToMail() {
        return offlineToMail;
    }
}
