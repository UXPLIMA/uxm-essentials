package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /warns <player>}: review a player's warning history newest-first. A read-only query against the
 * append-only history — it renders a header with the count, one entry per warning (issuer, reason, when), or
 * an empty notice when the target has none.
 */
public final class ReviewWarns {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;

    public ReviewWarns(ModerationRepository repository, ModerationNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render {@code target}'s warning history to {@code actor}, newest-first. */
    public void review(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<Warn> warns = repository.warns(target);
        if (warns.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.WARNS_EMPTY, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.WARNS_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(warns.size())));
        warns.forEach(warn -> notifier.send(actor, ModerationMessageKey.WARNS_ENTRY, entry(warn)));
    }

    private static Map<String, String> entry(Warn warn) {
        return Map.of(
                "issuer", warn.issuer().name(),
                "reason", warn.reason().orElse(""),
                "when", warn.issuedAt().toString());
    }
}
