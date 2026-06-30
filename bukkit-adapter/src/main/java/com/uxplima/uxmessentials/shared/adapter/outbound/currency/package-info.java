/**
 * The multi-currency provider seam: one façade over several economy back-ends, so a menu action or condition
 * can target any of them by a short spec string without ever importing a provider SDK. A
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.currency.CurrencyProvider} is a tiny capability over
 * {@code UUID}/{@code double} (balance, has, withdraw, deposit, format), and
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies} maps a spec —
 * {@code vault}, {@code exp}, {@code playerpoints}, {@code coinsengine:<name>}, {@code zessentials:<name>} — to the
 * provider that serves it, falling back to a configured default for a blank spec and to a logged no-op for an
 * unknown one.
 *
 * <p>Each back-end is reached the load-safest way available to it. Vault rides the already-resolved
 * {@code EconomyQuery} hook (a typed seam, no SDK type leaks past the hook). Exp is native Paper — a player's
 * experience points, no plugin needed, online players only. PlayerPoints, CoinsEngine and zEssentials are reached
 * purely by reflection behind a plugin-present guard, so their classes carry none of those SDK types and an absent
 * plugin loads nothing: every operation degrades to a safe no-op (and logs once) rather than throwing.
 *
 * <p>The capability is entity-thread: callers (Phase-2 economy actions, Phase-3 requirements) run on the viewer's
 * entity thread and invoke a provider there, the same thread Vault and the experience API require. The providers
 * add no scheduler hop and no main-thread blocking.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import org.jspecify.annotations.NullMarked;
