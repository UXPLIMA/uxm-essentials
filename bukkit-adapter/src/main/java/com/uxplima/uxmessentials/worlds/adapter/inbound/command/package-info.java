/**
 * The worlds context's inbound Brigadier command handlers: the {@code /worlds} root with its
 * create/import/load/unload/unregister/delete/confirm/list/info subcommand tree, where {@code /worlds confirm}
 * confirms a staged deletion (the target of the delete prompt's click). The literal is plural because
 * {@code playerstate} already owns {@code /world}; literals must be globally unique. Each subcommand maps a command source and its
 * arguments onto exactly one worlds use-case call and is gated by its own permission node via
 * {@code requires(...)}. World-mutating use cases run on the global region thread through the {@code Scheduler}
 * port; tab-completion reads only in-memory snapshots (the world registry, the async-refreshed importable
 * folder list). Player-facing feedback flows through the use cases' notifier, and only the synchronous
 * players-only rejection a console may see is rendered here, still through the catalog.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter.inbound.command;
