package com.uxplima.uxmessentials.bossbar.adapter.outbound.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.action.UxmBossBarActions;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarAnnouncements;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.application.ToggleBossBars;
import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published boss bar actions. Each one does what the matching {@code /bossbars} word does, through the same use
 * case and the same announcements, so a consumer and a staff member never disagree about a player's bars.
 */
@NullMarked
public final class BossBarActions implements UxmBossBarActions {

    private final ToggleBossBars toggle;
    private final BossBarVisibilityStore visibility;
    private final BossBarRenderer renderer;
    private final BossBarAnnouncements announcements;
    private final Supplier<BossBarConfig> bars;
    private final Runnable redrawEverybody;
    private final Supplier<Instant> clock;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    /** @param redrawEverybody draws every player's bars now, after an announcement */
    public BossBarActions(
            ToggleBossBars toggle,
            BossBarVisibilityStore visibility,
            BossBarRenderer renderer,
            BossBarAnnouncements announcements,
            Supplier<BossBarConfig> bars,
            Runnable redrawEverybody,
            Supplier<Instant> clock,
            PlayerLookup players,
            Scheduler scheduler) {
        this.toggle = Objects.requireNonNull(toggle, "toggle");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.bars = Objects.requireNonNull(bars, "bars");
        this.redrawEverybody = Objects.requireNonNull(redrawEverybody, "redrawEverybody");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> refresh(UUID playerId) {
        return onPlayer(subject(playerId), player -> {
            renderer.renderFor(player);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> hide(UUID playerId) {
        return set(playerId, true);
    }

    @Override
    public CompletableFuture<UxmOutcome> show(UUID playerId) {
        return set(playerId, false);
    }

    @Override
    public CompletableFuture<UxmOutcome> announce(String barId, Duration length) {
        UxmOutcome refused = refusal(barId, length);
        if (!refused.succeeded()) {
            return CompletableFuture.completedFuture(refused);
        }
        announcements.announce(idOf(barId), length, clock.get());
        redrawEverybody.run();
        return CompletableFuture.completedFuture(UxmOutcome.ok());
    }

    @Override
    public CompletableFuture<UxmOutcome> send(UUID playerId, String barId, Duration length) {
        PlayerRef who = subject(playerId);
        UxmOutcome refused = refusal(barId, length);
        if (!refused.succeeded()) {
            return CompletableFuture.completedFuture(refused);
        }
        return onPlayer(who, player -> {
            announcements.send(who.uuid(), idOf(barId), length, clock.get());
            renderer.renderFor(player);
            return UxmOutcome.ok();
        });
    }

    /** {@link UxmOutcome#ok()} when the bar is written and the length is positive, the refusal otherwise. */
    private UxmOutcome refusal(String barId, Duration length) {
        Objects.requireNonNull(barId, "barId");
        Objects.requireNonNull(length, "length");
        if (bars.get().byId(barId).isEmpty()) {
            return UxmOutcome.failed(UxmFailure.NOT_FOUND, "no boss bar is named " + barId);
        }
        if (length.isZero() || length.isNegative()) {
            return UxmOutcome.failed(UxmFailure.REFUSED, "an announcement must last some time");
        }
        return UxmOutcome.ok();
    }

    /** The id as the file writes it, which is what an announcement is kept under. */
    private String idOf(String barId) {
        return bars.get().byId(barId).map(bar -> bar.id()).orElse(barId);
    }

    private CompletableFuture<UxmOutcome> set(UUID playerId, boolean hidden) {
        PlayerRef who = subject(playerId);
        return onPlayer(who, player -> {
            if (visibility.hidden(who) == hidden) {
                return UxmOutcome.failed(
                        UxmFailure.ALREADY_IN_STATE,
                        hidden ? "their boss bars are already hidden" : "their boss bars are already shown");
            }
            toggle.set(who, hidden);
            renderer.renderFor(player);
            return UxmOutcome.ok();
        });
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, Objects.requireNonNull(playerId, "playerId"));
    }

    private CompletableFuture<UxmOutcome> onPlayer(PlayerRef who, Function<Player, UxmOutcome> write) {
        UxmOutcome gone = UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player is not online");
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    Player player = Bukkit.getPlayer(who.uuid());
                    return player == null ? gone : write.apply(player);
                },
                gone);
    }
}
