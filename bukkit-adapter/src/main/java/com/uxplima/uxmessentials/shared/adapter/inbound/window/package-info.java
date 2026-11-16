/**
 * Confirms a window to the player only once it is really showing. An inventory open can be cancelled by another
 * plugin or by the invsee edit guard, and a uxmLib window may open a tick late, so "opened" is said after the fact.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import org.jspecify.annotations.NullMarked;
