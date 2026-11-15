/**
 * The Vault outbound adapter, the legacy, single-currency compatibility bridge from a Vault economy into
 * this plugin's {@code EconomyProvider} port. This package and the sibling {@code treasury} package hold the
 * economy's provider-SDK bridges ({@code net.milkbowl.vault..}); {@code economyDomainHasNoProviderSdk} in
 * {@code LayerDependenciesTest} keeps the SDK out of the economy domain and application. Vault has no notion of
 * multiple currencies, so the adapter serves exactly the configured default currency and refuses any other
 * with {@code CURRENCY_UNSUPPORTED}: there is no implicit cross-currency conversion.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter.vault;
