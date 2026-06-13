package com.uxplima.uxmessentials.vote.application;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.SiteCooldown;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;

/**
 * Answers whether a player has at least one vote site available right now. The adapter calls this
 * on login (when reminders are enabled) to decide whether to prompt the player to vote.
 */
public final class VoteReminderEligibility {

    private final VoteRepository repository;
    private final VoteSiteCatalog catalog;

    public VoteReminderEligibility(VoteRepository repository, VoteSiteCatalog catalog) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * True when {@code player} can vote on at least one configured site at {@code now}. False when
     * the catalog is empty or every site is still on cooldown.
     */
    public boolean canVoteSomewhere(PlayerRef player, Instant now) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(now, "now");
        for (VoteSiteSpec spec : catalog.sites()) {
            SiteCooldown cd =
                    new SiteCooldown(spec.name(), repository.lastVoteAtSite(player, spec.name()), spec.cooldown());
            if (cd.isVotable(now)) {
                return true;
            }
        }
        return false;
    }
}
