/**
 * The homes context's inbound Brigadier command handlers — {@code /home}, {@code /sethome},
 * {@code /delhome}, {@code /homes}, {@code /renamehome}, {@code /movehome}, {@code /homeadmin}. Each maps a
 * command source and its arguments onto exactly one homes use-case call; all player-facing feedback flows
 * through the use cases' {@code MessageSink}, and only the players-only rejection a console may see is
 * rendered here, still through the catalog.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.adapter.inbound.command;
