/**
 * The invrollback context's outbound adapters:
 * {@link com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec} is the anti-corruption
 * layer between a live inventory's {@code ItemStack[]} (the main inventory plus, optionally, the ender chest) and
 * the domain's opaque snapshot bytes. It is the only place the context serializes an {@code ItemStack}; the
 * {@code :core} layer never sees one.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.invrollback.adapter.outbound;
