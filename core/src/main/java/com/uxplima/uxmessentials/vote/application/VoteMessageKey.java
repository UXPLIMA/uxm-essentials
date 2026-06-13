package com.uxplima.uxmessentials.vote.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vote context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VOTE_THANKS} ↔ {@code vote.thanks}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere
 * in the context — every message resolves through one of these.
 *
 * <p>The vote-links shown by {@code /vote} and the reward commands run on a vote are operator-authored
 * config content under {@code modules/vote/config.conf}, rendered/dispatched as written and never
 * parity-checked. Only the plugin's own strings — the thank-you broadcast, the {@code /vote} and
 * {@code /voteparty} framing, the test-reward confirmation, and the players-only rejection — are keys.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum VoteMessageKey implements MessageKey {

    // thank-you broadcast on a credited vote
    VOTE_THANKS("vote.thanks"),

    // /vote header + the per-link entry + the no-links-configured line
    VOTE_LINKS_HEADER("vote.links.header"),
    VOTE_LINKS_ENTRY("vote.links.entry"),
    VOTE_LINKS_EMPTY("vote.links.empty"),

    // /voteparty status line and the party-fired broadcast
    VOTEPARTY_STATUS("voteparty.status"),
    VOTEPARTY_REACHED("voteparty.reached"),

    // /vote testreward confirmation
    VOTE_TESTREWARD("vote.testreward"),

    // console rejection — only a player can run /vote or /voteparty
    VOTE_PLAYERS_ONLY("vote.players-only"),

    // /votes <player> — per-player totals across all periods
    VOTE_TOTAL("vote.total"),

    // /votes unknown player
    VOTE_TOTAL_UNKNOWN("vote.total.unknown"),

    // /votetop header, per-row entry, and empty-state
    VOTE_TOP_HEADER("vote.top.header"),
    VOTE_TOP_ENTRY("vote.top.entry"),
    VOTE_TOP_EMPTY("vote.top.empty");

    private final String key;

    VoteMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
