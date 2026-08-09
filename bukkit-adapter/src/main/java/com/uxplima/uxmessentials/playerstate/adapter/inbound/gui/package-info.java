/**
 * The playerstate context's GUI. {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView},
 * {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.EnderseeView} and
 * {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineContainerView} back {@code /invsee} and
 * {@code /endersee} against an online or offline target with a managed menu that mirrors the target's container
 * into a private copy the viewer edits, then reconciles that copy back onto the target on close. The viewer never
 * holds a handle to the target's live container, which is what closes the classic raw-inventory dupe window.
 *
 * <p>All four of those windows are the same two menu specs, wired by
 * {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorWindow}: the file owns the chrome, and
 * {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.MirrorContent} owns the item slots the file
 * hands over as a content region, where the movement rules and the write-back live.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;
