/**
 * The moderation context's bukkit-side wiring and shared adapter types: {@link
 * com.uxplima.uxmessentials.moderation.adapter.ModerationWiring} constructs the use cases over the kernel
 * ports and the persistence DSL and produces the command/listener registrations, and {@link
 * com.uxplima.uxmessentials.moderation.adapter.ModerationServices} bundles the constructed use cases for the
 * inbound adapter. The cross-context gate bridges ({@code MutePolicy}/{@code JailGate}) are bound here onto
 * the rebindable holders the messaging and teleport contexts already hold.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.moderation.adapter;
