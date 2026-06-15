/**
 * The npc context's inbound listeners: the {@code NpcLifecycleListener} that drives the renderer's per-viewer
 * spawn/forget tracking across join, quit, and world change so a viewer never keeps a ghost NPC, and the
 * {@code NpcInteractionListener} that runs an NPC's bound click command when a player interacts with its (server-
 * unknown) fake entity.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.adapter.inbound.listener;
