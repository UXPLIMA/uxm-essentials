/**
 * The presence context's outbound ports. {@code PresenceStore} is the transient per-player presence map (a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} mutated through {@code compute}); {@code VisibilityApplier}
 * drives Bukkit's {@code hide}/{@code show} graph so a vanished player disappears from everyone who lacks the
 * vanish-see node (the same graph the messaging and teleport {@code canSee} reads honour); {@code
 * PresenceAudience} enumerates the online players who receive an AFK away/back broadcast. The adapters
 * (in-memory, Bukkit) live in the bukkit module.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.application.port;
