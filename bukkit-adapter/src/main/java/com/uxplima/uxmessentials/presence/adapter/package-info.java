/**
 * The presence context's bukkit-adapter wiring: {@code PresenceWiring} constructs the AFK/vanish use cases over
 * the kernel ports and the context's own in-memory presence store, the {@code BukkitVisibilityApplier} (driving
 * the {@code hide}/{@code show} graph that the messaging {@code /msg} and teleport {@code /tpa} vanish checks
 * read through {@code canSee}), and the online-player audience for AFK broadcasts. It produces the Brigadier
 * commands, the move/chat activity and join/quit lifecycle listeners, and the self-rescheduling AFK idle sweep.
 * The {@code Plugin} handle is needed only for the per-viewer {@code hidePlayer} call; the adapters take the
 * {@code Plugin} interface and the kernel ports, nothing from bootstrap.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter;
