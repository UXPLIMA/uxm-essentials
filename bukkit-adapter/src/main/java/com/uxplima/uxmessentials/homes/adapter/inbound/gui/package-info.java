/**
 * The homes context's GUI: {@link com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenuView} opens the
 * read-only {@code /homes} browse menu as a uxmLib {@code PaginatedGui}, one display icon per home the viewer
 * owns (drawn from the {@code ListHomes.homesOf} read the chat list shares) with paged previous/next buttons.
 * Clicking an icon teleports the viewer to that home through the same {@code TeleportHome} use case {@code /home}
 * drives.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.adapter.inbound.gui;
