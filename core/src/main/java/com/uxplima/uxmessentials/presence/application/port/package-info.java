/**
 * The presence context's outbound ports. {@code PresenceStore} is the transient per-player presence map (a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} mutated through {@code compute}); {@code PresenceAudience}
 * enumerates the online players who receive an AFK away/back broadcast; {@code NickStore} holds the per-player
 * nickname. Hiding a vanished player from everyone else is the vanish context's job, not one of these. The
 * adapters (in-memory, Bukkit) live in the bukkit module.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.application.port;
