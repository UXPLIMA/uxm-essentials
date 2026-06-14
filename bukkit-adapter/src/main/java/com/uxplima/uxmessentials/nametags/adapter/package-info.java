/**
 * The nametags context's adapter root: the operator-content holder
 * ({@link com.uxplima.uxmessentials.nametags.adapter.NametagSettings}) and its HOCON codec, plus the wiring
 * ({@link com.uxplima.uxmessentials.nametags.adapter.NametagsWiring}) that constructs the presenter, the reconcile
 * timer, and the connection listener over the kernel ports and uxmLib's packet nametag renderer. The nametag has no
 * per-player toggle, so the context publishes no command.
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.adapter;

import org.jspecify.annotations.NullMarked;
