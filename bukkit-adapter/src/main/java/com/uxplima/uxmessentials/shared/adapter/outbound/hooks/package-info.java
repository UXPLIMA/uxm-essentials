/**
 * This plugin's optional integrations: Vault's economy and permissions, and HeadDatabase. Each one is a
 * {@link com.uxplima.uxmlib.hook.Integration} that names its plugin and resolves to a typed capability with a
 * safe no-op default, and uxmLib's {@link com.uxplima.uxmlib.hook.Integrations} resolves all of them once at
 * enable and hands a caller the capability by class.
 *
 * <p>The model was written here and moved into uxmLib on 2026-09-22, because it is a mechanism every plugin
 * with an optional dependency can use. What stays here is what this plugin integrates with.
 *
 * <p>Every integration keeps the other plugin's types behind {@code whenPresent}, and the no-op default names
 * none of them, so a server without the plugin never constructs the real side and never loads its classes.
 * The {@code HookHarness} in the matching test package is the present and absent harness.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import org.jspecify.annotations.NullMarked;
