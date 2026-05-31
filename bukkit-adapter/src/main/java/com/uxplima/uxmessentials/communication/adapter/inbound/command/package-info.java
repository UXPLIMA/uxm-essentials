/**
 * The communication context's inbound Brigadier commands: the static {@code /broadcasttoggle} (the plugin's own
 * per-player announcer opt-out, confirmed through a {@code CommunicationMessageKey}) and one dynamic info-page
 * command per configured page ({@code /rules}, {@code /motd}, {@code /info}, …), each guarded by
 * {@code uxmessentials.communication.info.<name>} and rendering operator-authored MiniMessage content. Each
 * handler maps its source to one use-case or info-sender call; no domain rule lives here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.adapter.inbound.command;
