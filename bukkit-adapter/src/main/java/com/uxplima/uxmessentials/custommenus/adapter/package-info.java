/**
 * The {@code custommenus} context's adapter layer: {@code CustomMenuLoader}, which reads the operator's
 * {@code menus/*.conf} files, parses each through the Phase-1 {@code MenuSpecLoader}, validates its refs
 * against the registered {@code MenuBindings}, and registers the survivors with {@code Menus}. A file that
 * fails to parse or names an unknown binding is skipped with a logged warning rather than aborting the load,
 * so one bad operator menu never hides the rest.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter;
