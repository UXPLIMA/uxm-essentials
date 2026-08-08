package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Serves the {@code /vote} display: the operator-configured list of vote-site links. The links are
 * config content, so they are held here as a plain list and rendered through the per-link
 * {@code VOTE_LINKS_ENTRY} key; the header and the no-links-configured line are the plugin's own keys.
 * When no links are configured the empty line is shown so {@code /vote} is never silent.
 */
public final class VoteLinks {

    private final List<String> links;
    private final Notifier notifier;

    public VoteLinks(List<String> links, Notifier notifier) {
        this.links = List.copyOf(Objects.requireNonNull(links, "links"));
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show the configured vote links (or the empty line when none are configured) to {@code viewer}. */
    public void show(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (links.isEmpty()) {
            notifier.send(viewer, VoteMessageKey.VOTE_LINKS_EMPTY);
            return;
        }
        notifier.send(viewer, VoteMessageKey.VOTE_LINKS_HEADER);
        for (String link : links) {
            notifier.send(viewer, VoteMessageKey.VOTE_LINKS_ENTRY, Map.of("link", link));
        }
    }

    /** The configured links, for callers that need the raw list. */
    public List<String> links() {
        return links;
    }
}
