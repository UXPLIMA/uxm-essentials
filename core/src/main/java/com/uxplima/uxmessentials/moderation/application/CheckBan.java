package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /checkban <player>}: report whether a player is currently banned, and if so the issuer, reason and
 * expiry of the active ban. A read-only point lookup against the DB-backed sanction store ({@code loadTempban})
 * — unlike {@code /banhistory}, which lists the full record, this answers the single "is this player banned
 * right now?" question staff ask before a fresh sanction. The command runs the read off the tick thread.
 *
 * <p>A permanent ban is stored as a far-future {@link TempbanState.Active} row, so the present-tense check is
 * simply whether a live ban row exists; the rendered expiry shows the stored instant either way.
 */
public final class CheckBan {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;

    public CheckBan(ModerationRepository repository, ModerationNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Report {@code target}'s current ban state to {@code viewer}. */
    public void show(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (repository.loadTempban(target) instanceof TempbanState.Active active) {
            notifier.send(
                    viewer,
                    ModerationMessageKey.MOD_CHECK_BANNED,
                    Map.of(
                            "player", target.name(),
                            "actor", active.issuer().name(),
                            "reason", active.reason().orElse(""),
                            "expiry", active.until().toString()));
            return;
        }
        notifier.send(viewer, ModerationMessageKey.MOD_CHECK_NOT_BANNED, Map.of("player", target.name()));
    }
}
