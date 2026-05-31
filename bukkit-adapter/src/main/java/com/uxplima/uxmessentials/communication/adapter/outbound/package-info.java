/**
 * The communication context's outbound Bukkit adapters: {@code PdcBroadcastOptOutStore} (the per-player
 * announcer opt-out bit, PDC-stamped so it survives relog), {@code AtomicSequenceCounter} (the per-channel
 * rotation index for sequential connection orderings), {@code ThreadLocalRandomSource} (the bounded random draw
 * the random orderings use), {@code BukkitAnnouncerBroadcaster} (the per-tick fan-out to online, opted-in
 * players), {@code BukkitInfoSender} (renders an info page to one viewer line by line), and {@code AnnouncerTask}
 * (the self-rescheduling announcer timer on the kernel {@code Scheduler} port). Each delivery hops to the viewer's
 * region thread inside the message sink.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.adapter.outbound;
