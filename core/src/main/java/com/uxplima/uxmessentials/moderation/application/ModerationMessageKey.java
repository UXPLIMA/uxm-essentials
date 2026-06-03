package com.uxplima.uxmessentials.moderation.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The moderation context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code MUTE_APPLIED} ↔ {@code moderation.mute.applied}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in
 * the context — every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum ModerationMessageKey implements MessageKey {

    // /mute, /tempmute, /unmute
    MUTE_APPLIED("moderation.mute.applied"),
    MUTE_APPLIED_TIMED("moderation.mute.applied-timed"),
    MUTE_NOTIFY_TARGET("moderation.mute.notify-target"),
    MUTE_ALREADY("moderation.mute.already"),
    UNMUTE_APPLIED("moderation.unmute.applied"),
    UNMUTE_NOT_MUTED("moderation.unmute.not-muted"),

    // /jail, /unjail
    JAIL_APPLIED("moderation.jail.applied"),
    JAIL_APPLIED_TIMED("moderation.jail.applied-timed"),
    JAIL_NOTIFY_TARGET("moderation.jail.notify-target"),
    JAIL_UNKNOWN("moderation.jail.unknown-jail"),
    JAIL_ALREADY("moderation.jail.already"),
    UNJAIL_APPLIED("moderation.unjail.applied"),
    UNJAIL_NOT_JAILED("moderation.unjail.not-jailed"),

    // /tempban
    TEMPBAN_APPLIED("moderation.tempban.applied"),
    TEMPBAN_KICK("moderation.tempban.kick"),

    // /ban, /unban — permanent UUID ban and its lift
    BAN_APPLIED("moderation.ban.applied"),
    BAN_KICK("moderation.ban.kick"),
    BAN_LIFTED("moderation.unban.applied"),
    BAN_NOT_BANNED("moderation.unban.not-banned"),

    // /kick, /kickall
    KICK_APPLIED("moderation.kick.applied"),
    KICK_KICKED("moderation.kick.kicked"),
    KICKALL_APPLIED("moderation.kickall.applied"),

    // /warn, /warns
    WARN_APPLIED("moderation.warn.applied"),
    WARN_NOTIFY_TARGET("moderation.warn.notify-target"),
    WARNS_HEADER("moderation.warns.header"),
    WARNS_ENTRY("moderation.warns.entry"),
    WARNS_EMPTY("moderation.warns.empty"),

    // /banip, /unbanip
    BANIP_APPLIED("moderation.banip.applied"),
    BANIP_ALTS_DETECTED("moderation.banip.alts-detected"),
    BANIP_KICK("moderation.banip.kick"),
    UNBANIP_APPLIED("moderation.unbanip.applied"),
    UNBANIP_NOT_BANNED("moderation.unbanip.not-banned"),

    // /freeze, /unfreeze
    FREEZE_APPLIED("moderation.freeze.applied"),
    FREEZE_NOTIFY_TARGET("moderation.freeze.notify-target"),
    UNFREEZE_APPLIED("moderation.unfreeze.applied"),
    UNFREEZE_NOTIFY_TARGET("moderation.unfreeze.notify-target"),
    FREEZE_BLOCKED("moderation.freeze.blocked"),

    // /sudo
    SUDO_DONE("moderation.sudo.done"),

    // /seen, /seenip
    SEEN_REPORT("moderation.seen.report"),
    SEEN_NEVER("moderation.seen.never"),
    SEENIP_REPORT("moderation.seenip.report"),
    SEENIP_ALTS("moderation.seenip.alts"),
    SEENIP_NO_IP("moderation.seenip.no-ip"),

    // shared moderation failures
    TARGET_EXEMPT("moderation.target-exempt"),
    BAD_DURATION("moderation.bad-duration"),
    UNKNOWN_TARGET("moderation.unknown-target"),
    MUTED_COMMAND_BLOCKED("moderation.muted-command-blocked");

    private final String key;

    ModerationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
