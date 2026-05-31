/**
 * The vaults context's outbound adapters: the {@code com.uxplima.uxmessentials.audit} logging audit for the
 * staff-override open, and {@link com.uxplima.uxmessentials.vaults.adapter.outbound.VaultItemCodec}, the
 * anti-corruption codec that serializes a live vault inventory's {@code ItemStack[]} into the domain's opaque
 * contents payload (the one part of a vault that serializes, per the persistence invariant).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.adapter.outbound;
