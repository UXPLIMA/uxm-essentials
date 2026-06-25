/**
 * The worlds context's GUI editor, rendered through the menu engine. The
 * {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldListMenu} world picker, the per-world
 * {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainMenu} hub, the
 * {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldCreateMenu} new-world screen, the read-only
 * {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGenerationMenu} summary, and the shared
 * rules/access {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldGridMenu} each register their
 * {@code world-*} spec and bindings with the engine and open through {@code menus.open(...)}; the engine's one
 * holder/listener owns every window. Each screen hands the world (or an in-flight create draft) in as the menu
 * subject, so the engine renders the labels from the worlds editor catalog without the screens touching a port
 * off-thread.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;
