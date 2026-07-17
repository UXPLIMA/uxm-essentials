/**
 * The server-tweaks context's Bukkit listeners, each a no-op unless its own tweak is on.
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.ServerBrandJoinListener} re-sends the
 * configured F3 server brand to each joiner ({@code f3-brand}).
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.UnsignedChatListener} re-delivers signed
 * public chat as unsigned messages ({@code no-chat-reports}). The SignedVelocity trio
 * ({@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityChatListener},
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityCommandListener},
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityQuitListener}) apply a Velocity
 * proxy's chat/command rulings and forget them on quit ({@code signed-velocity}).
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import org.jspecify.annotations.NullMarked;
