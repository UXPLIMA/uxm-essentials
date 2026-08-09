package com.uxplima.uxmessentials.api.action;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmWarn;

/**
 * Handing down and lifting punishments.
 *
 * <p>Every verb here runs the same use case the matching command runs, so a ban laid down by a plugin is a ban in
 * every sense the server knows: the player is disconnected, the punishment is stored, the history line is written,
 * the operator audit records it, and any listener that watches for one hears it. The issuer recorded against it is
 * the plugin that asked, which is what {@code /baninfo} and {@code /history} then show.
 *
 * <p>Exemption is honoured. A target the operator has made exempt is {@link UxmFailure#REFUSED} here exactly as
 * they are for a staff member typing the command, because exemption is about who may be punished rather than about
 * who is asking. Duration limits are not: those cap what a given rank may hand down, and a plugin holds no rank.
 *
 * <p>The punishment is announced to the server unless you ask otherwise. A plugin that punishes routinely, an
 * anti-cheat writing its own announcements for instance, should work through {@link #silently()} so the server is
 * not told twice.
 */
public interface UxmModerationActions {

    /**
     * The same actions with the server-wide announcement suppressed.
     *
     * <p>The punishment still lands, is still audited, and the player is still told. Only the broadcast is left
     * out. Jail sentences are unaffected: whether jailing announces is the module's own setting.
     */
    UxmModerationActions silently();

    /** Ban this player until somebody lifts it, answering the ban as it now stands. */
    CompletableFuture<UxmResult<UxmSanction>> ban(UUID targetId, String reason);

    /** Ban this player with no reason recorded. */
    CompletableFuture<UxmResult<UxmSanction>> ban(UUID targetId);

    /**
     * Ban this player for {@code duration}, answering the ban as it now stands.
     *
     * <p>A duration of zero or less is {@link IllegalArgumentException}: a ban that has already lapsed is not a
     * punishment, and a permanent one is {@link #ban(UUID, String)}.
     */
    CompletableFuture<UxmResult<UxmSanction>> tempBan(UUID targetId, Duration duration, String reason);

    /** Ban this player for {@code duration} with no reason recorded. */
    CompletableFuture<UxmResult<UxmSanction>> tempBan(UUID targetId, Duration duration);

    /** Lift a ban, or {@link UxmFailure#NOT_FOUND} when the player is not banned. */
    CompletableFuture<UxmOutcome> unban(UUID targetId);

    /** Mute this player until somebody lifts it. */
    CompletableFuture<UxmResult<UxmSanction>> mute(UUID targetId, String reason);

    /** Mute this player with no reason recorded. */
    CompletableFuture<UxmResult<UxmSanction>> mute(UUID targetId);

    /** Mute this player for {@code duration}, which must be positive. */
    CompletableFuture<UxmResult<UxmSanction>> tempMute(UUID targetId, Duration duration, String reason);

    /** Mute this player for {@code duration} with no reason recorded. */
    CompletableFuture<UxmResult<UxmSanction>> tempMute(UUID targetId, Duration duration);

    /** Lift a mute, or {@link UxmFailure#NOT_FOUND} when the player is not muted. */
    CompletableFuture<UxmOutcome> unmute(UUID targetId);

    /**
     * Disconnect this player with a reason they will see.
     *
     * <p>{@link UxmFailure#PLAYER_OFFLINE} when there is nobody to disconnect, which is worth telling apart from a
     * refusal: nothing was wrong with the request, the player had already gone.
     */
    CompletableFuture<UxmOutcome> kick(UUID targetId, String reason);

    /** Disconnect this player with no reason given. */
    CompletableFuture<UxmOutcome> kick(UUID targetId);

    /**
     * Record a warning against this player, answering the warning it recorded.
     *
     * <p>A warning carries nothing but its reason, so one is required. The operator's escalation rules apply: the
     * warning that crosses a threshold also applies whatever punishment that threshold names.
     */
    CompletableFuture<UxmResult<UxmWarn>> warn(UUID targetId, String reason);

    /** Jail this player in the named jail until somebody releases them. */
    CompletableFuture<UxmResult<UxmSanction>> jail(UUID targetId, String jail, String reason);

    /** Jail this player in the named jail for {@code duration}, which must be positive. */
    CompletableFuture<UxmResult<UxmSanction>> jail(UUID targetId, String jail, Duration duration, String reason);

    /** Release a jailed player, or {@link UxmFailure#NOT_FOUND} when they are not serving a sentence. */
    CompletableFuture<UxmOutcome> unjail(UUID targetId);
}
