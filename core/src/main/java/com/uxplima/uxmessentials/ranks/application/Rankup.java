package com.uxplima.uxmessentials.ranks.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /rankup}: advance a player one rung up the ladder when they meet the next rank's requirements and can
 * pay its cost. The current standing is resolved through {@link CurrentRank} (defaulting to the first rank for a
 * player with no stored pointer), the next rank through {@link com.uxplima.uxmessentials.ranks.domain.RankLadder#next},
 * and the outcome is returned as a typed {@link RankupResult} the command boundary renders — this use case sends
 * no message and touches no Bukkit type.
 *
 * <h2>Ordering and rollback stance</h2>
 * The steps run in a deliberate order so a refused rankup leaves no trace: <b>requirements → charge → advance →
 * actions</b>.
 * <ol>
 *   <li><b>Requirements.</b> Every parsed requirement of the next rank is evaluated first; a single failure
 *       returns {@link RankupResult#requirementsNotMet()} with nothing charged and the pointer unmoved.</li>
 *   <li><b>Charge.</b> Only once the requirements pass is the cost charged through the {@link RankEconomy} seam.
 *       A refused debit (insufficient funds) returns {@link RankupResult#cannotAfford()} — the withdraw reported
 *       {@code false}, so no money moved and there is nothing to compensate; the pointer does not advance. With
 *       no economy provider wired, or a zero cost, the charge is skipped and rankup is free.</li>
 *   <li><b>Advance.</b> The rank pointer is saved (the prestige level is carried over unchanged — prestige is a
 *       separate mechanic).</li>
 *   <li><b>Actions.</b> The rank's configured actions run last, only after the advance is durable.</li>
 * </ol>
 * Charging strictly before advancing is what guarantees a failed charge never advances. The one residual gap is
 * the exceptional case where the guarded debit succeeds but the subsequent {@code save} throws a genuine
 * database fault: Phase 2 does not auto-refund there — {@code save} is a single-row idempotent upsert, so a fault
 * is exceptional and surfaces to the command boundary rather than being silently swallowed. A compensating
 * refund is deferred rather than papered over.
 */
public final class Rankup {

    /** Rank costs are charged in the economy's default currency; a per-rank currency is out of Phase-2 scope. */
    private static final String DEFAULT_CURRENCY = "default";

    private final CurrentRank currentRank;
    private final PlayerRankRepository repository;
    private final com.uxplima.uxmessentials.ranks.domain.RankLadder ladder;
    private final RankRequirementEvaluator requirements;
    private final RankActionRunner actions;
    private final Optional<RankEconomy> economy;

    public Rankup(
            CurrentRank currentRank,
            PlayerRankRepository repository,
            com.uxplima.uxmessentials.ranks.domain.RankLadder ladder,
            RankRequirementEvaluator requirements,
            RankActionRunner actions,
            Optional<RankEconomy> economy) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /** Attempt to advance {@code who} one rank up the ladder, returning the typed outcome. */
    public RankupResult rankUp(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Optional<RankStanding> standing = currentRank.of(who.uuid());
        if (standing.isEmpty()) {
            return RankupResult.alreadyMax();
        }
        Optional<Rank> next = ladder.next(standing.get().rank().id());
        if (next.isEmpty()) {
            return RankupResult.alreadyMax();
        }
        return advance(who, standing.get(), next.get());
    }

    private RankupResult advance(PlayerRef who, RankStanding standing, Rank target) {
        if (!requirements.passesAll(who, parseRequirements(target))) {
            return RankupResult.requirementsNotMet();
        }
        if (!charge(who, target)) {
            return RankupResult.cannotAfford();
        }
        repository.save(who.uuid(), target.id(), standing.prestige());
        actions.run(who, target.actions());
        return RankupResult.rankedUp(target);
    }

    /** Charge the rank's cost, or pass through free when the cost is zero or no economy provider is wired. */
    private boolean charge(PlayerRef who, Rank target) {
        if (target.cost() <= 0L || economy.isEmpty()) {
            return true;
        }
        return economy.get().withdraw(who, BigDecimal.valueOf(target.cost()), DEFAULT_CURRENCY);
    }

    /** Parse the rank's raw requirement lines, dropping any the codec cannot recognise (not enforced). */
    private List<RankRequirement> parseRequirements(Rank target) {
        return target.requirements().stream()
                .map(RankRequirement::parse)
                .flatMap(Optional::stream)
                .toList();
    }
}
