package com.uxplima.uxmessentials.api.bukkit.event;

import org.bukkit.event.Event;

import org.jspecify.annotations.NullMarked;

/**
 * The base of every uxmEssentials notification event: something that has already happened.
 *
 * <p>It is delivered on a tick thread, and on Folia specifically on the region thread that owns whatever the event is
 * about, so a listener may use the Bukkit API freely. Delivery is scheduled rather than inline, so an event can
 * arrive a tick after the change it describes; the change is committed by then, which means what you read is the new
 * state and there is nothing to cancel. Cancellable pre-events are the other half of the pair, one per action worth
 * vetoing, and they extend {@link UxmCancellableEvent}.
 *
 * <p>Most facts are about a player and extend {@link UxmPlayerEvent}, which names them. The ones that extend this
 * class directly are the genuinely server-wide ones, where no single player is the subject: a world loading, a vote
 * party firing, the announcer reloading.
 */
@NullMarked
public abstract class UxmEvent extends Event {

    protected UxmEvent() {
        super(false); // synchronous: the bridge already scheduled this onto a tick thread
    }
}
