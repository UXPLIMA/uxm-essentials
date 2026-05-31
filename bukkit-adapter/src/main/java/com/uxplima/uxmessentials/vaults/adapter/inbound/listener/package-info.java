/**
 * The vaults context's inbound listener: the {@code InventoryCloseEvent} save that serializes a closed vault
 * window's live slots and writes them through to the DB region-safely, tracking the open windows so the
 * module's {@code stop()} flushes them on disable.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.adapter.inbound.listener;
