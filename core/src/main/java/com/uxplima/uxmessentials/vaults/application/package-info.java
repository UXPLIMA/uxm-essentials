/**
 * Application layer of the vaults bounded context: the use cases that orchestrate the {@code Vault} aggregate
 * through the {@code VaultRepository} and {@code VaultAudit} ports — {@code OpenVault} (resolves the amount and
 * size quotas through the shared {@code Permissions} reducer and loads or allocates the requested vault),
 * {@code ListVaults}, {@code OpenAdminVault} (the audit-logged staff override), and {@code SaveVault} (the
 * write-through on close). The {@code VaultsModule} ({@code FeatureModule}), the {@code VaultsMessageKey}
 * catalog handles, and the command-surface table also live here. No Bukkit, Paper, Kyori, or logging type
 * appears — the layer talks only to the domain and the kernel/context ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.application;
