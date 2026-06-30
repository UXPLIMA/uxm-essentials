/**
 * The unified text-input seam. {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput} is the one
 * entry point a GUI uses to capture a line of text; the operator chooses per input point whether it is captured through
 * an anvil or through chat ({@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputSettings}, read from
 * {@code text-input.conf}). Both backends recognise the same configurable cancel keywords, and the seam hops the
 * result onto the viewer's region thread so a call site's callback is always Folia-safe.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import org.jspecify.annotations.NullMarked;
