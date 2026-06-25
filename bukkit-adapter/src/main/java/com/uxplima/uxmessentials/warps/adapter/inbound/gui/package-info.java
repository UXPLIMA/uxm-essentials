/**
 * The warps context's GUI: {@link com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu} renders the
 * read-only {@code /warp} browse menu through the menu engine, one display tile per usable warp (drawn from the
 * {@code ListWarps.available} filter the chat list shares) with paged previous/next buttons and category drill-in.
 * Clicking a warp tile warps the player to that warp through the same {@code UseWarp} use case {@code /warp} drives.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.adapter.inbound.gui;
