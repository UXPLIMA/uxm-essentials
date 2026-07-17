/**
 * The security context's Bukkit adapter: {@link com.uxplima.uxmessentials.security.adapter.SecurityWiring} assembles
 * the two-factor store (jOOQ, from the shared persistence handle), the enrolment use cases, and the {@code /2fa} and
 * {@code /pin} Brigadier commands over the injected kernel ports. The join-verification listener, op-command
 * protection and IP/alt guard land here in the later phases.
 */
@NullMarked
package com.uxplima.uxmessentials.security.adapter;

import org.jspecify.annotations.NullMarked;
