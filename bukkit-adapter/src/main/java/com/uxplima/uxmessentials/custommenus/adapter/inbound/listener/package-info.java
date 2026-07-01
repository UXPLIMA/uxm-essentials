/**
 * The custommenus context's inbound listeners: the opener-item mechanics that let an operator hand players a
 * clickable item which opens a custom menu. {@code MenuOpenerInteractListener} opens the tagged menu when its item
 * is right-clicked; {@code MenuOpenerJoinListener} hands the item out on join per each opener's give-on-join rule.
 * Both read only the public {@code Menus} façade and the loaded-menu-name set the {@code /menu} command uses.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;
