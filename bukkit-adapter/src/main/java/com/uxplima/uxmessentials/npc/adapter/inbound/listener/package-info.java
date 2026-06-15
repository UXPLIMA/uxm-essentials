/**
 * The npc context's inbound listeners: the {@code NpcLifecycleListener} that drives the renderer's per-viewer
 * spawn/forget tracking across join, quit, and world change so a viewer never keeps a ghost NPC. Click
 * interaction is wired in a later sub-phase.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.adapter.inbound.listener;
