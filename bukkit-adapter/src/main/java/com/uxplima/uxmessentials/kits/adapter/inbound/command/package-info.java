/**
 * The kits context's inbound Brigadier command surface: a single {@code /kit} command whose {@code <name>}
 * positional claims a kit and whose {@code list}, {@code show}, {@code create}, {@code del}, {@code editor}
 * and {@code reset} literals fold in the former standalone commands, each gated by its own permission node
 * via {@code .requires(...)}. The command builds its node, maps its arguments, and delegates to a kits use
 * case; the claim feedback flows through the use cases' message sink, so the handler holds the catalog only
 * for the players-only and unknown-player rejections a console may see. {@code KitCommands} hands it to the
 * plugin's {@code COMMANDS} lifecycle handler.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.adapter.inbound.command;
