/**
 * The server-tweaks context's inbound adapters: the Bukkit event listeners that carry a tweak's effect into the
 * running server (the F3-brand join listener, the unsigned-chat listener, and the SignedVelocity chat/command/quit
 * listeners live under {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener}), plus the
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.inbound.SignedVelocityChannelListener} that receives the
 * proxy's rulings on the {@code signedvelocity:main} plugin-message channel.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.adapter.inbound;

import org.jspecify.annotations.NullMarked;
