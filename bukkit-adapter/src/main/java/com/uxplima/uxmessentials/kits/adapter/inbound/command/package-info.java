/**
 * The kits context's inbound Brigadier command handlers: {@code /kit}, {@code /kits}, {@code /showkit},
 * {@code /createkit}, {@code /delkit}, {@code /kiteditor}, and {@code /kitreset}. Each handler builds its
 * command node, maps its arguments, and delegates to a kits use case; the claim feedback flows through the
 * use cases' message sink, so a handler holds the catalog only for the players-only and unknown-player
 * rejections a console may see. {@code KitCommands} collects them in surface order for the plugin's
 * {@code COMMANDS} lifecycle handler.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.adapter.inbound.command;
