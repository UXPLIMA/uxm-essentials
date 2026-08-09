/**
 * How the published views become JSON.
 *
 * <p>One class, {@link com.uxplima.uxmessentials.rest.view.Views}, with one method per view type, so a home looks
 * the same whether it came from a list or a lookup. Times are ISO-8601, durations are whole seconds under a
 * {@code -seconds} name, and an absent value is present and {@code null} rather than a missing key.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.view;
