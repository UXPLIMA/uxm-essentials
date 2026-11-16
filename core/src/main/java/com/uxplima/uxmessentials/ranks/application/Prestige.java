package com.uxplima.uxmessentials.ranks.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.ranks.application.RanksConfig.PrestigeSettings;
import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerPrestiged;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /prestige}: a player at the very top of the ladder resets back to the first rank in exchange for a
 * permanent prestige level and its reward multiplier. It runs {@link Rankup}'s pipeline (eligibility, requirements,
 * charge, save, actions) on the same threads, and reuses the same requirement evaluator, action runner and economy
 * seam a rankup goes through. This class holds no Bukkit type and sends no message; it returns a typed
 * {@link PrestigeResult} the command boundary renders.
 *
 * <h2>Ordering and threads</h2>
 * <ol>
 *   <li><b>Check</b>, on the caller's thread, which must own the player: the standing must sit on the top rung
 *       ({@link PrestigeResult#notAtTop()} otherwise), the level must be below the configured {@code max-level}
 *       ({@link PrestigeResult#maxLevel()}), and the requirements that read the live player (inventory,
 *       placeholder, permission, previous rank) must pass. A refusal here has charged and written nothing.</li>
 *   <li><b>Commit</b>, on the async lane: the requirements that read storage (playtime, balance) are checked, then
 *       the cost is charged in the default currency, then the pointer is reset to {@link RankLadder#first()} at the
 *       next level in one {@code save}. A save that throws refunds the charge in the same currency, so a player
 *       never pays for a prestige they did not get, and the answer is {@link PrestigeResult#failed()}. A refund the economy refuses or fails is queued as owed
 *       ({@link OwedCredits}) in the same currency and paid on the next join.</li>
 *   <li><b>Finish</b>, back on the player's thread: the prestige actions run and {@link PlayerPrestiged} is
 *       published, both only after the reset is durable.</li>
 * </ol>
 * The returned future completes on the player's thread, or on the caller's thread when the check refuses. A player
 * who left while the commit ran gets the future completed on the scheduler's retired branch, which may be off the
 * tick, so a callback checks the player is still online before it touches them. It never completes exceptionally.
 *
 * <p>Only one prestige per player is in flight at a time, so two quick commands cannot both pay for the same level.
 * The standing comes from memory through {@link CurrentRank#of}, so a player whose pointer has not loaded yet gets
 * {@link PrestigeResult#busy()}.
 */
public final class Prestige {

    /** Prestige costs are charged in the economy's default currency, exactly as a rank's rankup cost is. */
    private static final String DEFAULT_CURRENCY = "default";

    private final CurrentRank currentRank;
    private final PlayerRankRepository repository;
    private final RankLadder ladder;
    private final RankRequirementEvaluator requirements;
    private final RankActionRunner actions;
    private final Optional<RankEconomy> economy;
    private final PrestigeSettings settings;
    private final DomainEventPublisher events;
    private final Scheduler scheduler;
    private final Logger log;
    private final ChargeRefunds refunds;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public Prestige(
            CurrentRank currentRank,
            PlayerRankRepository repository,
            RankLadder ladder,
            RankRequirementEvaluator requirements,
            RankActionRunner actions,
            Optional<RankEconomy> economy,
            Optional<OwedCredits> owed,
            PrestigeSettings settings,
            DomainEventPublisher events,
            Scheduler scheduler,
            Logger log) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.refunds = new ChargeRefunds(economy, owed, log);
    }

    /**
     * Attempt to prestige {@code who}, resetting them to the first rank, and answer the typed outcome. Call it on
     * the thread that owns {@code who}.
     */
    public CompletableFuture<PrestigeResult> prestige(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        UUID id = who.uuid();
        if (!inFlight.add(id)) {
            return CompletableFuture.completedFuture(PrestigeResult.busy());
        }
        CompletableFuture<PrestigeResult> answer = new CompletableFuture<>();
        // The caller chains on the released future, so a follow-up prestige from its callback is not refused as busy.
        CompletableFuture<PrestigeResult> released = answer.whenComplete((result, failure) -> inFlight.remove(id));
        try {
            check(who, answer);
        } catch (RuntimeException failure) {
            log.error("prestige check failed for player " + id, failure);
            answer.complete(PrestigeResult.failed());
        } catch (Error error) {
            answer.complete(PrestigeResult.failed());
            throw error;
        }
        return released;
    }

    private void check(PlayerRef who, CompletableFuture<PrestigeResult> answer) {
        Optional<RankStanding> standing = currentRank.of(who.uuid());
        if (standing.isEmpty()) {
            // With a non-empty ladder an empty standing only means the pointer has not loaded yet.
            answer.complete(ladder.isEmpty() ? PrestigeResult.notAtTop() : PrestigeResult.busy());
            return;
        }
        if (ladder.next(standing.get().rank().id()).isPresent()) {
            answer.complete(PrestigeResult.notAtTop());
            return;
        }
        if (!standing.get().prestige().belowCap(settings.maxLevel())) {
            answer.complete(PrestigeResult.maxLevel());
            return;
        }
        List<RankRequirement> needs = parseRequirements();
        if (!requirements.passesLive(who, needs)) {
            answer.complete(PrestigeResult.requirementsNotMet());
            return;
        }
        Rank first = ladder.first().orElseThrow(); // non-empty: the standing already resolved a ladder rank
        Reset reset = new Reset(who, first, standing.get().prestige().increment(), needs, price());
        scheduler.async(() -> commit(reset, answer));
    }

    /** Check stored requirements, charge and save on the async lane, then hop back to the player's thread. */
    private void commit(Reset reset, CompletableFuture<PrestigeResult> answer) {
        PrestigeResult result = PrestigeResult.failed();
        boolean handedOff = false;
        try {
            result = chargeAndSave(reset);
            PrestigeResult settled = result;
            scheduler.onEntity(
                    reset.who(),
                    () -> finish(reset, settled, true, answer),
                    () -> finish(reset, settled, false, answer));
            handedOff = true;
        } finally {
            // Last resort: an Error, or a scheduler that refuses the hop while the plugin shuts down, must still
            // answer, or the in-flight guard would refuse this player's every later prestige. The answer is what the
            // commit decided (a saved prestige is durable), or failed when the commit did not finish.
            if (!handedOff) {
                answer.complete(result);
            }
        }
    }

    private PrestigeResult chargeAndSave(Reset reset) {
        PlayerRef who = reset.who();
        boolean charged = false;
        try {
            if (!requirements.passesStored(who, reset.needs())) {
                return PrestigeResult.requirementsNotMet();
            }
            if (reset.isPriced()) {
                if (!economy.orElseThrow().withdraw(who, reset.cost(), DEFAULT_CURRENCY)) {
                    return PrestigeResult.cannotAfford();
                }
                charged = true;
            }
            repository.save(who.uuid(), reset.first().id(), reset.level());
            return PrestigeResult.prestiged(reset.level(), reset.level().rewardMultiplier(settings.rewardMultiplier()));
        } catch (RuntimeException failure) {
            log.error("prestige could not be stored for player " + who.uuid(), failure);
            if (charged) {
                refund(reset);
            }
            return PrestigeResult.failed();
        } catch (Error error) {
            // A dying thread still gives back what it took: the save did not answer, so nothing was granted.
            if (charged) {
                refund(reset);
            }
            throw error;
        }
    }

    private void refund(Reset reset) {
        refunds.giveBack(reset.who(), reset.cost(), DEFAULT_CURRENCY, "prestige");
    }

    /**
     * On the player's thread: run the actions and raise the event when the reset was saved, then answer. When the
     * player left before the hop there is nobody to run actions on, but the event is still raised because the new
     * level is durable.
     */
    private void finish(Reset reset, PrestigeResult result, boolean present, CompletableFuture<PrestigeResult> answer) {
        try {
            if (result.status() == PrestigeResult.Status.PRESTIGED) {
                if (present) {
                    runActions(reset);
                }
                events.publish(new PlayerPrestiged(reset.who(), reset.level().level(), result.rewardMultiplier()));
            }
        } catch (RuntimeException failure) {
            log.error("prestige event failed for player " + reset.who().uuid(), failure);
        } finally {
            // Always answered, or the in-flight guard would refuse this player's every later prestige.
            answer.complete(result);
        }
    }

    private void runActions(Reset reset) {
        try {
            actions.run(reset.who(), settings.actions());
        } catch (RuntimeException failure) {
            // The prestige is already paid for and saved; a broken action line must not hide that from the player.
            log.error("prestige actions failed for player " + reset.who().uuid(), failure);
        }
    }

    /** The prestige cost, or zero when it is free or no economy provider is wired. */
    private BigDecimal price() {
        if (settings.cost() <= 0L || economy.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(settings.cost());
    }

    /** Parse the prestige requirement lines, dropping any the codec cannot recognise (not enforced). */
    private List<RankRequirement> parseRequirements() {
        return settings.requirements().stream()
                .map(RankRequirement::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    /** One checked prestige on its way to storage: who, the rung and level it resets to, what it asks and costs. */
    private record Reset(
            PlayerRef who,
            Rank first,
            com.uxplima.uxmessentials.ranks.domain.Prestige level,
            List<RankRequirement> needs,
            BigDecimal cost) {

        boolean isPriced() {
            return cost.signum() > 0;
        }
    }
}
