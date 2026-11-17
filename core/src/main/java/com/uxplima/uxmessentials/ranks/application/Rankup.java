package com.uxplima.uxmessentials.ranks.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerRankedUp;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /rankup}: advance a player one rung up the ladder when they meet the next rank's requirements and can
 * pay its cost. The current standing is resolved from memory through {@link CurrentRank}, the next rank through
 * {@link RankLadder#next}, and the outcome is returned as a typed {@link RankupResult} the caller renders. This use
 * case sends no message and touches no Bukkit type.
 *
 * <h2>Ordering and threads</h2>
 * <ol>
 *   <li><b>Check</b>, on the caller's thread, which must own the player: the standing and the next rank's live
 *       requirements (inventory, placeholder, permission, previous rank), which only that thread may read. A
 *       failure here returns with nothing charged and nothing written.</li>
 *   <li><b>Commit</b>, on the async lane: the requirements that read storage (playtime, balance) are checked, then
 *       the cost is charged, then the pointer is saved. A failed requirement or a refused debit ends the attempt
 *       with nothing moved. A save that throws refunds the charge, so a player never pays for a rank they did not
 *       get. A refund the economy refuses or fails is queued as owed
 *       ({@link OwedCredits}) in the same currency and paid on the next join.</li>
 *   <li><b>Finish</b>, back on the player's thread: the rank's actions run and the {@link PlayerRankedUp} event is
 *       published, both only after the advance is durable. A player who left in the meantime has the actions kept
 *       for their next join on any server ({@link RankActions}).</li>
 * </ol>
 * The returned future completes on the player's thread, or on the caller's thread when the check refuses, so a
 * caller may message the player from it. The one exception is a player who left while the commit ran: the future
 * then completes on whatever thread the scheduler runs its retired branch on, which may be off the tick, so a
 * callback checks the player is still online before it touches them. It never completes exceptionally: a storage
 * fault is {@link RankupResult#failed()}.
 *
 * <p>Only one rankup per player is in flight at a time. Without that guard two quick clicks would both pass the
 * check against the same standing and both pay for the same rung.
 */
public final class Rankup {

    /** Rank costs are charged in the economy's default currency. */
    private static final String DEFAULT_CURRENCY = "default";

    private final CurrentRank currentRank;
    private final PlayerRankRepository repository;
    private final RankLadder ladder;
    private final RankRequirementEvaluator requirements;
    private final RankActions actions;
    private final Optional<RankEconomy> economy;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;
    private final Logger log;
    private final ChargeRefunds refunds;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public Rankup(
            CurrentRank currentRank,
            PlayerRankRepository repository,
            RankLadder ladder,
            RankRequirementEvaluator requirements,
            RankActions actions,
            Optional<RankEconomy> economy,
            Optional<OwedCredits> owed,
            DomainEventPublisher events,
            Scheduler scheduler,
            Logger log) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.refunds = new ChargeRefunds(economy, owed, log);
    }

    /** Attempt to advance {@code who} one rank up the ladder, charging the next rank's cost. */
    public CompletableFuture<RankupResult> rankUp(PlayerRef who) {
        return rankUp(who, true);
    }

    /**
     * Attempt to advance {@code who} one rank up the ladder. This is the seam the autorank scan reuses: with
     * {@code charge} {@code false} the cost step is skipped and a met rankup promotes for free. With {@code charge}
     * {@code true} the behaviour is identical to {@code /rankup}. Call it on the thread that owns {@code who}.
     */
    public CompletableFuture<RankupResult> rankUp(PlayerRef who, boolean charge) {
        Objects.requireNonNull(who, "who");
        UUID id = who.uuid();
        if (!inFlight.add(id)) {
            return CompletableFuture.completedFuture(RankupResult.busy());
        }
        CompletableFuture<RankupResult> answer = new CompletableFuture<>();
        // The caller chains on the released future, so a follow-up rankup from its callback is not refused as busy.
        CompletableFuture<RankupResult> released = answer.whenComplete((result, failure) -> inFlight.remove(id));
        try {
            check(who, charge, answer);
        } catch (RuntimeException failure) {
            log.error("rankup check failed for player " + id, failure);
            answer.complete(RankupResult.failed());
        } catch (Error error) {
            answer.complete(RankupResult.failed());
            throw error;
        }
        return released;
    }

    private void check(PlayerRef who, boolean charge, CompletableFuture<RankupResult> answer) {
        Optional<RankStanding> standing = currentRank.of(who.uuid());
        if (standing.isEmpty()) {
            // With a non-empty ladder an empty standing only means the pointer has not loaded yet.
            answer.complete(ladder.isEmpty() ? RankupResult.alreadyMax() : RankupResult.busy());
            return;
        }
        Optional<Rank> next = ladder.next(standing.get().rank().id());
        if (next.isEmpty()) {
            answer.complete(RankupResult.alreadyMax());
            return;
        }
        Rank target = next.get();
        List<RankRequirement> needs = parseRequirements(target);
        if (!requirements.passesLive(who, needs)) {
            answer.complete(RankupResult.requirementsNotMet());
            return;
        }
        Advance advance = new Advance(who, standing.get(), target, needs, charge ? price(target) : BigDecimal.ZERO);
        scheduler.async(() -> commit(advance, answer));
    }

    /** Check stored requirements, charge and save on the async lane, then hop back to the player's thread. */
    private void commit(Advance advance, CompletableFuture<RankupResult> answer) {
        RankupResult result = RankupResult.failed();
        boolean handedOff = false;
        try {
            result = chargeAndSave(advance);
            RankupResult settled = result;
            scheduler.onEntity(
                    advance.who(),
                    () -> finish(advance, settled, true, answer),
                    () -> finish(advance, settled, false, answer));
            handedOff = true;
        } finally {
            // Last resort: an Error, or a scheduler that refuses the hop while the plugin shuts down, must still
            // answer, or the in-flight guard would refuse this player's every later rankup. The answer is what the
            // commit decided (a saved rankup is durable), or failed when the commit did not finish.
            if (!handedOff) {
                answer.complete(result);
            }
        }
    }

    private RankupResult chargeAndSave(Advance advance) {
        PlayerRef who = advance.who();
        boolean charged = false;
        try {
            if (!requirements.passesStored(who, advance.needs())) {
                return RankupResult.requirementsNotMet();
            }
            if (advance.isPriced()) {
                if (!economy.orElseThrow().withdraw(who, advance.cost(), DEFAULT_CURRENCY)) {
                    return RankupResult.cannotAfford();
                }
                charged = true;
            }
            repository.save(who.uuid(), advance.target().id(), advance.from().prestige());
            return RankupResult.rankedUp(advance.target());
        } catch (RuntimeException failure) {
            log.error("rankup could not be stored for player " + who.uuid(), failure);
            if (charged) {
                refund(advance);
            }
            return RankupResult.failed();
        } catch (Error error) {
            // A dying thread still gives back what it took: the save did not answer, so nothing was granted.
            if (charged) {
                refund(advance);
            }
            throw error;
        }
    }

    private void refund(Advance advance) {
        refunds.giveBack(advance.who(), advance.cost(), DEFAULT_CURRENCY, "rankup");
    }

    /**
     * On the player's thread: run the actions and raise the event when the rank was saved, then answer. When the
     * player left before the hop the actions are kept for their next join, and the event is still raised because the
     * new rank is durable.
     */
    private void finish(Advance advance, RankupResult result, boolean present, CompletableFuture<RankupResult> answer) {
        try {
            if (result.status() == RankupResult.Status.RANKED_UP) {
                actions.deliver(advance.who(), advance.target().actions(), present);
                events.publish(new PlayerRankedUp(
                        advance.who(),
                        advance.from().rank().id(),
                        advance.target().id()));
            }
        } catch (RuntimeException failure) {
            log.error("rankup event failed for player " + advance.who().uuid(), failure);
        } finally {
            // Always answered, or the in-flight guard would refuse this player's every later rankup.
            answer.complete(result);
        }
    }

    /** The rank's cost, or zero when it is free or no economy provider is wired. */
    private BigDecimal price(Rank target) {
        if (target.cost() <= 0L || economy.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(target.cost());
    }

    /** Parse the rank's raw requirement lines, dropping any the codec cannot recognise (not enforced). */
    private static List<RankRequirement> parseRequirements(Rank target) {
        return target.requirements().stream()
                .map(RankRequirement::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    /** One checked rankup on its way to storage: who, from where, to where, what it asks for and what it costs. */
    private record Advance(
            PlayerRef who, RankStanding from, Rank target, List<RankRequirement> needs, BigDecimal cost) {

        boolean isPriced() {
            return cost.signum() > 0;
        }
    }
}
