/**
 * The security context's application layer: the {@link com.uxplima.uxmessentials.security.application.SecurityModule
 * feature module}, its typed config, and the two-factor enrolment use cases (begin/confirm a TOTP secret, set a PIN,
 * disable a factor), with the join-verification flow, op-command protection and the IP/alt guard behind the same
 * module. Pure orchestration over the {@code domain} maths and the outbound {@code port}s, no Bukkit, no Adventure,
 * no jOOQ.
 */
@NullMarked
package com.uxplima.uxmessentials.security.application;

import org.jspecify.annotations.NullMarked;
