package com.uxplima.uxmessentials.messaging.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The messaging context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code MSG_SENT} ↔ {@code msg.sent}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context —
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum MessagingMessageKey implements MessageKey {

    // private messages — /msg, /reply
    MSG_SENT("msg.sent"),
    MSG_RECEIVED("msg.received"),
    MSG_SPY("msg.spy"),
    MSG_SELF("msg.self"),
    MSG_TARGET_OFFLINE("msg.target-offline"),
    MSG_TOGGLED_OFF("msg.toggled-off"),
    MSG_MUTED("msg.muted"),
    MSG_IGNORED("msg.ignored"),
    MSG_NO_REPLY_TARGET("msg.no-reply-target"),
    MSG_TARGET_AFK("msg.target-afk"),
    MSG_SENT_TO_MAIL("msg.sent-to-mail"),

    // /msgtoggle
    MSG_TOGGLE_ON("msg.toggle-on"),
    MSG_TOGGLE_OFF("msg.toggle-off"),

    // /rtoggle
    REPLY_TOGGLE_ON("msg.rtoggle-on"),
    REPLY_TOGGLE_OFF("msg.rtoggle-off"),

    // /ignore, /unignore
    IGNORE_ADDED("ignore.added"),
    IGNORE_REMOVED("ignore.removed"),
    IGNORE_SELF("ignore.self"),
    IGNORE_NOT_IGNORED("ignore.not-ignored"),
    IGNORE_LIST_HEADER("ignore.list.header"),
    IGNORE_LIST_ENTRY("ignore.list.entry"),
    IGNORE_LIST_EMPTY("ignore.list.empty"),

    // /socialspy
    SOCIALSPY_ON("socialspy.on"),
    SOCIALSPY_OFF("socialspy.off"),
    SOCIALSPY_WATCHING("socialspy.watching"),
    SOCIALSPY_UNWATCHED("socialspy.unwatched"),

    // /mail
    MAIL_SENT("mail.sent"),
    MAIL_NEW_NOTIFY("mail.new-notify"),
    MAIL_READ_HEADER("mail.read.header"),
    MAIL_READ_ENTRY("mail.read.entry"),
    MAIL_EMPTY("mail.empty"),
    MAIL_CLEARED("mail.cleared"),
    MAIL_SENDALL_DONE("mail.sendall"),

    // /helpop
    HELPOP_SENT("helpop.sent"),
    HELPOP_RECEIVED("helpop.received"),
    HELPOP_NO_STAFF("helpop.no-staff");

    private final String key;

    MessagingMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
