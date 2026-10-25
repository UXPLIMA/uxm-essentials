package com.uxplima.uxmessentials.bossbar.adapter.outbound.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.api.query.UxmBossBarQuery;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/** The published boss bar query, read off the same file, choice and screen the module draws from. */
@NullMarked
public final class BossBarQueries implements UxmBossBarQuery {

    private final Supplier<BossBarConfig> bars;
    private final BossBarVisibilityStore visibility;
    private final BossBarRenderer renderer;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public BossBarQueries(
            Supplier<BossBarConfig> bars,
            BossBarVisibilityStore visibility,
            BossBarRenderer renderer,
            PlayerLookup players,
            Scheduler scheduler) {
        this.bars = Objects.requireNonNull(bars, "bars");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<String> bars() {
        return bars.get().bars().stream().map(BossBarDefinition::id).toList();
    }

    @Override
    public CompletableFuture<Optional<Boolean>> hidden(UUID playerId) {
        PlayerRef who = subject(playerId);
        return AsyncQueries.onPlayer(
                scheduler,
                who,
                () -> online(who) ? Optional.of(visibility.hidden(who)) : Optional.empty(),
                Optional.empty());
    }

    @Override
    public CompletableFuture<List<String>> shown(UUID playerId) {
        PlayerRef who = subject(playerId);
        return AsyncQueries.onPlayer(
                scheduler, who, () -> online(who) ? renderer.shownIds(who.uuid()) : List.of(), List.of());
    }

    @Override
    public CompletableFuture<Optional<Duration>> remaining(UUID playerId, String barId) {
        Objects.requireNonNull(barId, "barId");
        PlayerRef who = subject(playerId);
        return AsyncQueries.onPlayer(
                scheduler,
                who,
                () -> online(who) ? renderer.remaining(who.uuid(), barId) : Optional.empty(),
                Optional.empty());
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, Objects.requireNonNull(playerId, "playerId"));
    }

    private static boolean online(PlayerRef who) {
        return Bukkit.getPlayer(who.uuid()) != null;
    }
}
