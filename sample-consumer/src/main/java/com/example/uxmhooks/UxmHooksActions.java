package com.example.uxmhooks;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmSanction;

/**
 * Asking uxmEssentials to do things, which is the third half of the API.
 *
 * <p>The write surface is taken with the calling plugin, not without it: every write is attributed, so the audit
 * line, the ban record and the warp owner all name whoever asked. There is no anonymous form.
 *
 * <p>Nothing throws for a reason the server understood. An action answers with an outcome carrying a stable code,
 * and the codes are constants on {@link UxmFailure} so a branch survives a rewording. An exception means something
 * broke; a malformed call (a null id, a negative amount, a blank message) throws where you are standing, because
 * that is a bug rather than an answer.
 *
 * <p>Every action returns a {@code CompletableFuture} and hops to the right thread by itself, so calling one from
 * a listener is safe. Chain from the future rather than joining it.
 */
public final class UxmHooksActions {

    private final Plugin plugin;
    private final Logger log;

    public UxmHooksActions(Plugin plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
    }

    /** Paying somebody out, which is the shape most of these have. */
    public void payReward(UxmEssentialsApi api, UUID playerId) {
        UxmActions actions = api.actions(plugin);
        actions.economy()
                .ifPresent(economy -> economy.deposit(playerId, new BigDecimal("250"))
                        .thenAccept(result -> result.ifFailed(this::logFailure)));
    }

    /**
     * Punishing somebody, and telling apart the two answers worth telling apart.
     *
     * <p>{@code already-in-state} means the player is already muted, which is usually not worth logging as a
     * problem. {@code refused} means a rule said no: they are exempt, or something else declined.
     */
    public void muteForFlooding(UxmEssentialsApi api, UUID playerId) {
        api.actions(plugin).moderation().ifPresent(moderation -> moderation
                .tempMute(playerId, Duration.ofMinutes(10), "flooding chat")
                .thenAccept(this::logMuteOutcome));
    }

    private void logMuteOutcome(UxmResult<UxmSanction> result) {
        result.ifFailed(failure -> {
            if (failure.is(UxmFailure.ALREADY_IN_STATE)) {
                log.fine("already muted, nothing to do");
            } else {
                logFailure(failure);
            }
        });
    }

    /**
     * Two writes where the second only makes sense once the first has happened.
     *
     * <p>The world action's future completes when the world is actually loaded, and the teleport's when the player
     * has actually landed, so chaining them reads the way the sequence happens.
     */
    public CompletableFuture<UxmOutcome> openTheArena(UxmEssentialsApi api, UUID playerId) {
        UxmActions actions = api.actions(plugin);
        return actions.worlds()
                .flatMap(worlds -> actions.teleport().map(teleport -> worlds.load("event_arena")
                        .thenCompose(loaded -> loaded.succeeded()
                                ? teleport.teleport(playerId, new UxmLocation("event_arena", 0, 64, 0))
                                : CompletableFuture.completedFuture(loaded))))
                .orElseGet(() -> CompletableFuture.completedFuture(
                        UxmOutcome.failed(UxmFailure.NOT_FOUND, "the worlds or teleport module is switched off")));
    }

    /**
     * Leaving a note that waits for the player however long it has to.
     *
     * <p>Sent without a sender id, so it arrives under this plugin's name rather than under somebody's account.
     * Nothing refuses it: there is no mute that applies to a plugin and no way for a player to ignore one.
     */
    public void mailTheWinner(UxmEssentialsApi api, UUID playerId) {
        api.actions(plugin)
                .messaging()
                .ifPresent(messaging -> messaging
                        .sendMail(playerId, "Your prize is waiting at spawn.")
                        .thenAccept(outcome -> outcome.ifFailed(this::logFailure)));
    }

    private void logFailure(UxmFailure failure) {
        log.warning("uxmEssentials declined (" + failure.code() + "): " + failure.message());
    }
}
