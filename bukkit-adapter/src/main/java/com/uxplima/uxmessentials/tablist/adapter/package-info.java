/**
 * The tablist context's adapter root: the operator-content holder
 * ({@link com.uxplima.uxmessentials.tablist.adapter.TablistSettings}) and its HOCON codec, plus the wiring
 * ({@link com.uxplima.uxmessentials.tablist.adapter.TablistWiring}) that constructs the renderer, the render timer,
 * and the connection listener over the kernel ports. The tablist has no per-player toggle, so the context publishes
 * no command.
 */
@NullMarked
package com.uxplima.uxmessentials.tablist.adapter;

import org.jspecify.annotations.NullMarked;
