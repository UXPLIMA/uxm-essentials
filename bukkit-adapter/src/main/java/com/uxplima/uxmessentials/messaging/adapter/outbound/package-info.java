/**
 * The messaging context's outbound Bukkit adapters: {@code BukkitMessageDelivery} (render + deliver the PM /
 * mail / spy / help-op lines through the kernel {@code Messages} + {@code MessageSink}), the PDC
 * {@code /msgtoggle} store, the in-memory socialspy and reply-target stores, the {@code /helpop} staff
 * audience, the {@code canSee}-based vanish-visibility gate (soft-coupled to presence), and the
 * self-rescheduling mail-expiry sweep. The mute gate is bound to {@code MutePolicy.NEVER} in wiring until the
 * moderation context lands.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.adapter.outbound;
