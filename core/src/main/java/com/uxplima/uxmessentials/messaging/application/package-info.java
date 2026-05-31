/**
 * Application layer of the messaging context: the {@code /msg} {@code /reply} {@code /mail} {@code /ignore}
 * {@code /socialspy} {@code /helpop} use cases that orchestrate the pure domain through outbound ports, the
 * {@code MessagingModule} {@code FeatureModule}, and the {@code MessagingMessageKey} catalog. Private
 * messages are transient/real-time (a transient reply-target store, the {@code MessageDelivery} port); the
 * mailbox and the ignore list are persistent ({@code MailRepository}, {@code IgnoreStore}). Cross-context
 * gates — mute (moderation) and vanish-aware target resolution (presence) — are soft-coupled lookups that
 * degrade to "no mute" / "fully visible" when the other module is disabled. No Bukkit, Paper, Kyori, or
 * logging type appears here; the layer is pure Java over the kernel ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.application;
