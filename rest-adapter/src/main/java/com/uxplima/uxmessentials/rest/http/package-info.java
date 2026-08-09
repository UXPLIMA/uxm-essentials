/**
 * The HTTP layer: read a request, find its route, run it, write an answer.
 *
 * <p>Hand-rolled on a {@link java.net.ServerSocket} rather than built on a framework, for two reasons. The surface
 * is one request per connection with a declared length and no chunking, no pipelining and no keep-alive, which is
 * small enough that every bound on it is a number somebody can read in
 * {@link com.uxplima.uxmessentials.rest.http.RequestReader}; and a plugin jar that drags an embedded server and its
 * transitive tree into a Paper classloader is a version conflict waiting for the first server that already has one.
 *
 * <p>Every answer shares one envelope, written in {@link com.uxplima.uxmessentials.rest.http.Json}. An operation
 * the server understood and declined comes back as {@code 200} carrying {@code ok:false} and the same failure code
 * the Java API returns, so a consumer branches on the same string in both places; the HTTP statuses are kept for
 * the things HTTP is actually about.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.http;
