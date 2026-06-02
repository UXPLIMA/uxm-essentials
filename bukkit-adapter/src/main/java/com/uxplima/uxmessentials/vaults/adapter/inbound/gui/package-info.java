/**
 * The vaults context's GUI: {@link
 * com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView} opens a vault as a uxmLib {@code StorageGui}
 * sized to the resolved quota with the decoded contents, captures the {@code (owner, vault)} identity per open
 * so the close handler writes that vault straight to the DB, and tracks the open windows so the module's
 * {@code stop()} flushes them on disable.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;
