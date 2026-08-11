/**
 * The presence context's bukkit-adapter wiring: {@code PresenceWiring} constructs the AFK use cases over the
 * kernel ports and the context's own in-memory presence store, and the online-player audience for AFK
 * broadcasts. It produces the Brigadier commands, the move/chat activity and join/quit lifecycle listeners, and
 * the self-rescheduling AFK idle sweep. The vanish state a presence reader reflects comes from the vanish
 * context; nothing here hides a player. The adapters take the {@code Plugin} interface and the kernel ports,
 * nothing from bootstrap.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter;
