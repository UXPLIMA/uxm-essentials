/**
 * The economy context's use cases — the orchestration above the pure domain. {@code Balance}, {@code Pay}
 * (with the per-currency confirm flow and {@code /paytoggle}), {@code BalTop}, and {@code EcoAdmin}
 * (give/take/set/reset plus the bulk giveall/giverandom/resetall) each reach money only through the
 * {@code EconomyProvider} port (or, for the exact-balance admin paths, the native {@code WalletRepository}),
 * never by touching another context's domain. {@code EconomyMessageKey} carries every user-visible string;
 * {@code EconomyModule} is the {@code FeatureModule} that wires them. No Bukkit, Paper, Kyori, logging,
 * Vault, or Treasury type appears here — the ArchUnit fence {@code economyDomainHasNoProviderSdk} keeps the
 * provider SDKs confined to the outbound adapter.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.application;
