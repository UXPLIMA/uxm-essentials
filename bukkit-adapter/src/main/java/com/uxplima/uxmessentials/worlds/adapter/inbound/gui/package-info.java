/**
 * The worlds context's GUI editor. {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldEditorHolder}
 * tags a {@code /worlds editor} window with the {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldEditorScreen}
 * it is showing and the world being edited, so the editor listener can recognise its own windows; the
 * {@code LIST} screen is the world picker and carries no world.
 * {@link com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldEditorText} resolves the editor's
 * {@code MessageKey} catalog entries into Adventure components in the viewer's locale, so every prompt and label
 * reads identically across the editor's screens.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;
