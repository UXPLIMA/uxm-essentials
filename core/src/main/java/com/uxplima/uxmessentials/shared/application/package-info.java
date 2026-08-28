/**
 * Application-layer services of the shared kernel: the use-case-shaped helpers that more than one bounded context
 * asks the same question of, and that would otherwise be answered three slightly different ways. The subpackages
 * carry the bulk of the kernel ({@code port} for the outbound ports every context calls, {@code message},
 * {@code command}, {@code permission}, {@code placeholder}, {@code module}, {@code health}, {@code claim},
 * {@code mapmarker}); what sits directly here is the small remainder that belongs to no single one of them.
 *
 * <p>Pure Java, like the rest of {@code core}: no Bukkit, Paper, Kyori or SLF4J type appears in this tree, and the
 * ArchUnit fences {@code applicationHasNoBukkit} and the kernel's own import rules hold that line.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.application;
