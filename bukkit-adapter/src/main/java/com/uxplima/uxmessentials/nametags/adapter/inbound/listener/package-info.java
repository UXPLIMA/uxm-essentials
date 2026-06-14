/**
 * The nametags context's inbound listener: the connection lifecycle listener
 * ({@link com.uxplima.uxmessentials.nametags.adapter.inbound.listener.NametagLifecycleListener}) that spawns a wearer's
 * nametag on join, despawns it on quit, and despawns-then-respawns it on a world change, every display mutation hopped
 * onto the wearer's region thread through the {@code Scheduler} port.
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.adapter.inbound.listener;

import org.jspecify.annotations.NullMarked;
