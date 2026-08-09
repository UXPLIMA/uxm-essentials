/**
 * The optional {@code uxmEssentials-rest} add-on: everything the developer API publishes, over HTTP.
 *
 * <p>It is its own Paper plugin, dormant until {@code config/rest.conf} says otherwise, and it compiles against the
 * published API and nothing else. That restriction is deliberate and load-bearing. A REST endpoint that cannot be
 * written without reaching into the host's internals is an endpoint whose Java equivalent a third-party plugin
 * could not write either, so the missing piece gets added to the published API, with its guard and its
 * documentation, rather than worked around here.
 *
 * <p>{@link com.uxplima.uxmessentials.rest.UxmEssentialsRest} enables it,
 * {@link com.uxplima.uxmessentials.rest.Routes} is the whole route table, and
 * {@link com.uxplima.uxmessentials.rest.TokenCommand} is where an operator makes the tokens that reach it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest;
