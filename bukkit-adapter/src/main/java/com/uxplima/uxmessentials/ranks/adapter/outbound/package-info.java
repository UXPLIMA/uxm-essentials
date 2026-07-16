/**
 * The ranks context's outbound Bukkit adapters: {@link com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankActionRunner}
 * parses a rank's configured action lines into the shared click-action model and runs them through the same
 * click-action engine the npc and hologram contexts use, and
 * {@link com.uxplima.uxmessentials.ranks.adapter.outbound.BukkitRankRequirementEvaluator} resolves each typed
 * rank requirement against the economy, playtime, permission, stored-rank, placeholder and inventory seams. The
 * economy cost seam is bridged in the economy context's {@code ProviderRankEconomy}, mirroring the kits bridge.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.adapter.outbound;
