/**
 * The nametags context's application ports: the soft-coupled
 * {@link com.uxplima.uxmessentials.nametags.application.port.NametagVanish} visibility gate the renderer asks before
 * showing a wearer's nametag to a viewer. The port degrades to "everyone can see everyone" when the presence context
 * is disabled, so the nametag never depends hard on presence. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.application.port;

import org.jspecify.annotations.NullMarked;
