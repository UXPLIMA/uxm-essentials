/**
 * Outbound ports of the discord-link bounded context: the {@code DiscordLinkStore} the use cases persist
 * pending codes and confirmed bindings through. The jOOQ-backed implementation lives in the persistence
 * adapter; the port carries only domain types so {@code :core} stays free of any persistence dependency.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.application.port;
