package com.uxplima.uxmessentials.discordlink.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The facts the discord-link context publishes: a binding was made, or a binding was removed.
 *
 * <p>A code that was issued and never redeemed is not one of them. It is a half-finished action, and a consumer
 * that acted on it would be acting on something that may never happen.
 */
public sealed interface DiscordLinkEvent extends DomainEvent permits AccountLinked, AccountUnlinked {}
