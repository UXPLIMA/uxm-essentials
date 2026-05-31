/**
 * The vaults context's adapter wiring: {@link com.uxplima.uxmessentials.vaults.adapter.VaultsWiring} constructs
 * the use cases over the kernel ports and the persistence DSL and produces the {@code /vault} command and the
 * {@code InventoryClose} save listener, {@link com.uxplima.uxmessentials.vaults.adapter.VaultServices} bundles
 * the constructed collaborators, and {@link com.uxplima.uxmessentials.vaults.adapter.VaultSettings} is the
 * typed {@code vaults.conf} view for the two numbered-quota defaults.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.adapter;
