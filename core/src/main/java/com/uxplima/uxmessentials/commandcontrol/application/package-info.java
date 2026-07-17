/**
 * The command-control context's application layer: the {@link com.uxplima.uxmessentials.commandcontrol.application
 * .CommandControlModule} feature-module identity and enable gate, the typed
 * {@link com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig} snapshot read once from the
 * module's scoped config, and the {@link com.uxplima.uxmessentials.commandcontrol.application
 * .CommandControlMessageKey} catalog of the user-visible deny lines. The layer orchestrates only through shared
 * ports and the pure {@code domain}; it names no Bukkit, Paper, Kyori, or SLF4J type.
 */
@NullMarked
package com.uxplima.uxmessentials.commandcontrol.application;

import org.jspecify.annotations.NullMarked;
