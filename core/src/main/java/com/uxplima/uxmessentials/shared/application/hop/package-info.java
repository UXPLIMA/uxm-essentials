/**
 * The cross-server hop every teleport kind shares: the origin charges, records a pending hop in the network-shared
 * store and hands the player to the proxy; the target resolves the hop through the kind's {@link
 * com.uxplima.uxmessentials.shared.application.hop.HopResolver} and moves the player, or the charge comes back.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.application.hop;
