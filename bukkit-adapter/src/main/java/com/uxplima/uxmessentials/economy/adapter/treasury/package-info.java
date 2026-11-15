/**
 * The Treasury outbound adapter, the modern, multi-currency bridge from a foreign Treasury economy into this
 * plugin's {@code EconomyProvider} port. This package and the sibling {@code vault} package hold the economy's
 * provider-SDK bridges ({@code me.lokka30.treasury..}); {@code economyDomainHasNoProviderSdk} in
 * {@code LayerDependenciesTest} keeps the SDK out of the economy domain and application. Treasury's asynchronous
 * subscriber API is bridged to the synchronous port off the tick thread, and Treasury currency identifiers
 * are translated to this plugin's {@code Currency} at this boundary so nothing above sees a Treasury type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter.treasury;
