package com.uxplima.uxmessentials.moderation.domain;

/**
 * The kind a {@link SanctionHistoryEntry} records — the high-level verb applied to a target. Four kinds,
 * matching the ban and mute families: {@link #BAN} and {@link #UNBAN} are the ban-family rows that
 * {@code /banhistory} surfaces, {@link #MUTE} and {@link #UNMUTE} the mute-family rows {@code /mutehistory}
 * surfaces.
 *
 * <p>The temporary/permanent and IP distinctions are <em>not</em> separate kinds: they are carried by the
 * entry's expiry and IP fields, mirroring how a {@code BanEntry} folds {@code /ban} and {@code /tempban} into
 * one row. A {@code /banip} is a {@link #BAN} with an IP present; an {@code /unbanip} an {@link #UNBAN} with
 * an IP present; a {@code /tempban} a {@link #BAN} with an expiry present.
 */
public enum SanctionAction {
    BAN,
    UNBAN,
    MUTE,
    UNMUTE
}
