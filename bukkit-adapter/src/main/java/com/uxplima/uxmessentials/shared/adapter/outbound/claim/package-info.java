/**
 * Outbound adapters binding the {@link com.uxplima.uxmessentials.shared.application.port.ClaimProvider}
 * port to whichever land-claim plugin is installed (Lands, GriefPrevention, or uxmClaims), plus the
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders} discoverer that picks
 * the active one and the {@link com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl}
 * that drives the shared {@code ClaimPolicy} from it.
 *
 * <p>Lands and GriefPrevention are {@code compileOnly} soft-depends, so their SDK types are absent from
 * the runtime and test classpaths. Each typed provider therefore reaches its SDK only past a
 * plugin-present guard, and the discoverer constructs a typed provider only once that plugin is known to
 * be loaded — a server (or test) without the plugin never loads the SDK classes. The uxmClaims provider
 * has no compile dependency at all and reaches its API purely by reflection.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import org.jspecify.annotations.NullMarked;
