package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;

import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;

/**
 * The shared placeholder mapping for one {@code /banhistory}, {@code /mutehistory} or {@code /history} entry
 * line, so the review use cases render a row identically. The {@code expires} placeholder is blank for a
 * permanent sanction, a lift or a kick and {@code ip} blank for a UUID-scoped row, so the catalog line
 * degrades cleanly without a separate key per shape.
 */
final class SanctionHistoryLine {

    private SanctionHistoryLine() {}

    static Map<String, String> of(SanctionHistoryEntry row) {
        return Map.of(
                "action", label(row.action()),
                "issuer", row.actor().name(),
                "reason", row.reason().orElse(""),
                "expires", row.expiry().map(Object::toString).orElse(""),
                "ip", row.ip().orElse(""),
                "when", row.at().toString());
    }

    static String label(SanctionAction action) {
        return switch (action) {
            case BAN -> "ban";
            case UNBAN -> "unban";
            case MUTE -> "mute";
            case UNMUTE -> "unmute";
            case WARN -> "warn";
            case KICK -> "kick";
        };
    }
}
