/**
 * The messaging context's outbound ports. {@code MailRepository} and {@code IgnoreStore} are the persistent
 * stores (DB-backed mail that survives restart, a persistent ignore list); {@code ConversationStore} holds
 * the transient {@code /reply} targets; {@code MessageDelivery} renders and delivers a private message,
 * mail notice, or help-op line to a viewer; {@code MutePolicy} and {@code VanishVisibility} are the
 * soft-coupled cross-context gates that degrade when moderation / presence are disabled. The adapters
 * (jOOQ + Caffeine, in-memory, Bukkit) live in the persistence and bukkit modules.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.application.port;
