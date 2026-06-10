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
    JAILS_HEADER("moderation.jails.header"),
    JAILS_ENTRY("moderation.jails.entry"),
    JAILS_EMPTY("moderation.jails.empty"),

    // /sanction — aggregated read-only punishment summary for one player
    SANCTION_HEADER("moderation.sanction.header"),
    SANCTION_MUTE_ACTIVE("moderation.sanction.mute-active"),
    SANCTION_MUTE_NONE("moderation.sanction.mute-none"),
    SANCTION_JAIL_ACTIVE("moderation.sanction.jail-active"),
    SANCTION_JAIL_NONE("moderation.sanction.jail-none"),
    SANCTION_BAN_ACTIVE("moderation.sanction.ban-active"),
    SANCTION_BAN_NONE("moderation.sanction.ban-none"),
    SANCTION_WARNS("moderation.sanction.warns"),

    // /setjail, /deljail — define and remove a stored jail location at the staff member's position
    SETJAIL_SAVED("moderation.setjail.saved"),
    DELJAIL_DELETED("moderation.deljail.deleted"),
    DELJAIL_NOT_FOUND("moderation.deljail.not-found"),

    // /jailedplayers — review of currently jailed players
    JAILEDLIST_HEADER("moderation.jailedlist-header"),
    JAILEDLIST_ENTRY("moderation.jailedlist-entry"),
    JAILEDLIST_EMPTY("moderation.jailedlist-empty"),

    // /tempban
    TEMPBAN_APPLIED("moderation.tempban.applied"),
    TEMPBAN_KICK("moderation.tempban.kick"),

    // /ban, /unban — permanent UUID ban and its lift
    BAN_APPLIED("moderation.ban.applied"),
    BAN_KICK("moderation.ban.kick"),
    BAN_LIFTED("moderation.unban.applied"),
    BAN_NOT_BANNED("moderation.unban.not-banned"),

    // /banlist — review of currently banned players
    BANLIST_HEADER("moderation.banlist-header"),
    BANLIST_ENTRY("moderation.banlist-entry"),
    BANLIST_EMPTY("moderation.banlist-empty"),

    // /mutelist — review of currently muted players
    MUTELIST_HEADER("moderation.mutelist-header"),
    MUTELIST_ENTRY("moderation.mutelist-entry"),
    MUTELIST_EMPTY("moderation.mutelist-empty"),

    // /banhistory — a player's full ban/unban history (newest-first)
    BANHISTORY_HEADER("moderation.banhistory.header"),
    BANHISTORY_ENTRY("moderation.banhistory.entry"),
    BANHISTORY_EMPTY("moderation.banhistory.empty"),

    // /mutehistory — a player's full mute/unmute history (newest-first)
    MUTEHISTORY_HEADER("moderation.mutehistory.header"),
    MUTEHISTORY_ENTRY("moderation.mutehistory.entry"),
    MUTEHISTORY_EMPTY("moderation.mutehistory.empty"),

    // /kick, /kickall
    KICK_APPLIED("moderation.kick.applied"),
    KICK_KICKED("moderation.kick.kicked"),
    KICKALL_APPLIED("moderation.kickall.applied"),

    // /warn, /tempwarn, /warns, /unwarn
    WARN_APPLIED("moderation.warn.applied"),
    TEMPWARN_APPLIED("moderation.tempwarn.applied"),
    WARN_NOTIFY_TARGET("moderation.warn.notify-target"),
    WARNS_HEADER("moderation.warns.header"),
    WARNS_ENTRY("moderation.warns.entry"),
    WARNS_EMPTY("moderation.warns.empty"),
    UNWARN_CLEARED("moderation.unwarn.cleared"),
    UNWARN_NONE("moderation.unwarn.none"),

    // /banip, /tempbanip, /unbanip
    BANIP_APPLIED("moderation.banip.applied"),
    TEMPBANIP_APPLIED("moderation.tempbanip.applied"),
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

    // /commandspy — staff toggle to watch the commands other players run
    COMMANDSPY_ON("moderation.commandspy.on"),
    COMMANDSPY_OFF("moderation.commandspy.off"),
    COMMANDSPY_OBSERVED("moderation.commandspy.observed"),

    // /alts — accounts sharing a target's last IP
    ALTS_HEADER("moderation.alts.header"),
    ALTS_ENTRY("moderation.alts.entry"),
    ALTS_NONE("moderation.alts.none"),
    ALTS_NO_IP("moderation.alts.no-ip"),

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
