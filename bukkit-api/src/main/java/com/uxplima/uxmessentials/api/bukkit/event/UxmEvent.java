package com.uxplima.uxmessentials.api.bukkit.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The base of every uxmEssentials notification event: something that has already happened.
 *
 * <p>It is delivered on a tick thread, and on Folia specifically on the region thread that owns whatever the event is
 * about, so a listener may use the Bukkit API freely. Delivery is scheduled rather than inline, so an event can
 * arrive a tick after the change it describes; the change is committed by then, which means what you read is the new
 * state and there is nothing to cancel. Cancellable pre-events are the other half of the pair, one per action worth
 * vetoing, and they extend {@link UxmCancellableEvent}.
 *
 * <p>Every event names the player it is about by id, because the player it concerns need not be online: a mail
 * delivery or a moderation action reaches an offline account just as well. {@link #getPlayer()} is the convenient
 * form when your listener only cares about online players.
 */
@NullMarked
public abstract class UxmEvent extends Event {

    private final UUID subjectId;
    private final String subjectName;

    protected UxmEvent(UUID subjectId, String subjectName) {
        super(false); // synchronous: the bridge already scheduled this onto a tick thread
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectName = Objects.requireNonNull(subjectName, "subjectName");
    }

    /** The id of the player this event is about. Always present, online or not. */
    public UUID getPlayerId() {
        return subjectId;
    }

    /** The name of the player this event is about, as uxmEssentials last knew it. */
    public String getPlayerName() {
        return subjectName;
    }

    /** The player this event is about, or {@code null} when they are offline. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(subjectId);
    }

    /** The player this event is about as an offline handle, which is never {@code null}. */
    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(subjectId);
    }
}
