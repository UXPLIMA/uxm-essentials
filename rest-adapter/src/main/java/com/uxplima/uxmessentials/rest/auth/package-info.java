/**
 * Who is asking, whether they may, and how often.
 *
 * <p>Tokens are made in game with {@code /uxmapi token create} and shown once. What is stored is a SHA-256 of the
 * secret, so the file an operator can read, back up and accidentally paste somewhere holds nothing that opens the
 * port. A lost token is revoked and made again.
 *
 * <p>A token carries scopes rather than a list of endpoints: {@code read} for every {@code GET}, {@code write} for
 * every {@code POST}, {@code events} for the stream. A panel that only draws graphs cannot move anybody's money,
 * including through an endpoint somebody adds next year.
 *
 * <p>The label the token was given is what the audit log records for the writes it makes, which is the point of
 * insisting on one: an operator asking who moved a balance gets the name of the thing that did it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.auth;
