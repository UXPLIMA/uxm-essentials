/**
 * The security context's outbound ports: the durable two-factor store. The application depends only on these
 * contracts; the jOOQ adapter (which hashes the PIN and encrypts the TOTP secret at rest) implements them.
 */
@NullMarked
package com.uxplima.uxmessentials.security.application.port;

import org.jspecify.annotations.NullMarked;
