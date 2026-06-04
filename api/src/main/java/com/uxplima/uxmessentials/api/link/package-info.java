/**
 * The account-linking seam between the host plugin and the optional Discord bridge. {@code
 * DiscordLinkConfirmation} is the host-registered, bridge-consumed {@code ServicesManager} service the bridge's
 * {@code /link} slash command redeems a code through; it carries only plain strings and a small enum so this
 * stays a pure-contract package with no persistence or Bukkit dependency.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.link;
