/**
 * The kits context's GUI. {@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView} opens the
 * read-only {@code /kits} browse menu as a uxmLib {@code PaginatedGui}, one display icon per claimable kit
 * (drawn from the {@code ListKits.available} filter the chat list shares) with paged previous/next buttons.
 *
 * <p>{@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView} opens the {@code /showkit}
 * read-only preview as a managed menu sized to fit the kit, with the kit's stacks laid out at their
 * definition-order slots ({@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitGuiLayout}) and the
 * trailing slots padded by a gray-glass filler; every interaction is cancelled by
 * {@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewListener}, recognised by the menu's
 * {@link com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewHolder}, so a player can look but never
 * take.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.adapter.inbound.gui;
