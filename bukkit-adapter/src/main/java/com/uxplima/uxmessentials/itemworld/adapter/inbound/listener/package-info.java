/**
 * The itemworld context's Bukkit listener: {@code PowertoolInteractListener} fires a held item's powertool
 * binding when the player clicks with it. It gates on the powertool sub-feature group + per-command disable
 * (live {@code itemworld.conf} view), the player's {@code /powertooltoggle} switch, and the presence of a
 * binding (read from item PDC), then runs the bound command lines as the player on their own region thread.
 * The toggle state is dropped on quit so a relog starts from the default.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.adapter.inbound.listener;
