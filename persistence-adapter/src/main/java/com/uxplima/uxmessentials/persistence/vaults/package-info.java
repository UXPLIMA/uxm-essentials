/**
 * The vaults context's outbound persistence adapter: the jOOQ {@link
 * com.uxplima.uxmessentials.persistence.vaults.JooqVaultRepository} over the generated V6 {@code vaults} table,
 * the {@link com.uxplima.uxmessentials.persistence.vaults.VaultRows} anti-corruption mapping (the queryable
 * owner/index/size/last-touched columns plus the base64-encoded opaque contents), the Caffeine-cached {@link
 * com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository}, and the {@link
 * com.uxplima.uxmessentials.persistence.vaults.VaultRepositories} factory the bukkit-adapter wires from without
 * naming a jOOQ type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.vaults;
