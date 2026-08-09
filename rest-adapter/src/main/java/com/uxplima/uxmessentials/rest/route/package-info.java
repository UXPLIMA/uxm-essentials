/**
 * The route tables, one class per context, each contributing its own list to
 * {@link com.uxplima.uxmessentials.rest.Routes}.
 *
 * <p>Split this way because the alternative is one file that grows with every feature the plugin gains and that
 * nobody can read in one sitting. Grouped by context, the paths for a feature sit next to each other and next to
 * the query surface they are built on, so adding an endpoint means opening one small file.
 *
 * <p>{@link com.uxplima.uxmessentials.rest.route.Reads} carries what every read route does: the module gate that
 * answers {@code 503} when a feature is switched off, the wait on a published future, the {@code 404} for a thing
 * that is not there, and the caps on how much one request may ask for.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.route;
