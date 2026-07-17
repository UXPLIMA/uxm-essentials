/**
 * The server-tweaks context's outbound adapters: the mechanics behind each tweak's effect. The F3-brand seam
 * ({@link com.uxplima.uxmessentials.servertweaks.adapter.outbound.ServerBrandSender} /
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.outbound.PluginMessageBrandSender}) encodes and sends the
 * {@code minecraft:brand} plugin message, and the console-filter seam
 * ({@link com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleSpamFilter} /
 * {@link com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleFilterInstaller}) puts the pure
 * {@link com.uxplima.uxmessentials.servertweaks.domain.ConsoleFilterPolicy} onto the server's Log4j2 pipeline.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import org.jspecify.annotations.NullMarked;
