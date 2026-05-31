package com.uxplima.uxmessentials.communication.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.ResolveDeathMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed communication use cases the listeners, the announcer timer, and the {@code /broadcasttoggle}
 * command share, built once per module start by {@code CommunicationWiring} over the kernel ports and the
 * context's own stores. Held so every collaborator reads the same use cases; the context persists nothing, so its
 * only adapter-side runtime state is the transient opt-out/sequence stores and the self-rescheduling announcer
 * task (stopped on disable).
 *
 * @param resolveJoin the join-channel policy resolution {@code ConnectionMessageListener} drives
 * @param resolveQuit the quit-channel policy resolution
 * @param resolveDeath the death-channel policy resolution {@code DeathMessageListener} drives
 * @param nextAnnouncement the rotation the announcer timer ticks
 * @param broadcastOptOut {@code /broadcasttoggle}
 */
@NullMarked
public record CommunicationServices(
        ResolveJoinMessage resolveJoin,
        ResolveQuitMessage resolveQuit,
        ResolveDeathMessage resolveDeath,
        NextAnnouncement nextAnnouncement,
        BroadcastOptOut broadcastOptOut) {

    public CommunicationServices {
        Objects.requireNonNull(resolveJoin, "resolveJoin");
        Objects.requireNonNull(resolveQuit, "resolveQuit");
        Objects.requireNonNull(resolveDeath, "resolveDeath");
        Objects.requireNonNull(nextAnnouncement, "nextAnnouncement");
        Objects.requireNonNull(broadcastOptOut, "broadcastOptOut");
    }
}
