package com.uxplima.uxmessentials.shared.application.permission;

import java.util.Map;
import java.util.Objects;

/**
 * The exemption nodes an operator's own files may still spell the way older versions did, each with the node the
 * plugin checks in its place.
 *
 * <p>A few settings take a node as their value ({@code pay.tax.bypass-permission}, {@code baltop.exempt-permission},
 * {@code spawn.exempt-permission}), and a file kept from an older version carries the earlier spelling there. Reading
 * those values through {@link #current} points them at the node the catalogue declares, so moving the grant in the
 * permission plugin is the only edit an operator has to make. Bukkit has no node aliases, so a grant itself cannot be
 * carried over the same way.
 */
public final class RenamedPermissions {

    /** The head a kept file may still put before a cooldown label's exemption. */
    private static final String LABEL_COOLDOWN_BYPASS = "uxmessentials.cooldown.bypass.";

    private static final Map<String, String> RENAMED = Map.ofEntries(
            Map.entry("uxmessentials.customcommand.cooldown.bypass", "uxmessentials.bypass.cooldown.customcommand"),
            Map.entry("uxmessentials.customcommand.cost.bypass", "uxmessentials.bypass.cost.customcommand"),
            Map.entry("uxmessentials.customcommand.warmup.bypass", "uxmessentials.bypass.warmup.customcommand"),
            Map.entry("uxmessentials.guard.bypass.gamemode", "uxmessentials.bypass.gamemode.guard"),
            Map.entry("uxmessentials.guard.bypass", "uxmessentials.bypass.guard"),
            Map.entry("uxmessentials.kit.cooldown.bypass", "uxmessentials.bypass.cooldown.kit"),
            Map.entry("uxmessentials.poses.cooldown.bypass", "uxmessentials.bypass.cooldown.poses"),
            Map.entry("uxmessentials.rtp.cooldown.bypass", "uxmessentials.bypass.cooldown.rtp"),
            Map.entry("uxmessentials.skin.cooldown.bypass", "uxmessentials.bypass.cooldown.skin"),
            Map.entry("uxmessentials.tp.cooldown.bypass", "uxmessentials.bypass.cooldown.tp"),
            Map.entry("uxmessentials.home.bypass.cost", "uxmessentials.bypass.cost.home"),
            Map.entry("uxmessentials.home.bypass.unsafe", "uxmessentials.bypass.safety.home"),
            Map.entry("uxmessentials.warp.bypass.lock", "uxmessentials.bypass.lock.warp"),
            Map.entry("uxmessentials.warp.bypass.password", "uxmessentials.bypass.password.warp"),
            Map.entry("uxmessentials.warp.bypass.safety", "uxmessentials.bypass.safety.warp"),
            Map.entry("uxmessentials.pwarp.bypass.ban", "uxmessentials.bypass.ban.pwarp"),
            Map.entry("uxmessentials.pwarp.bypass.password", "uxmessentials.bypass.password.pwarp"),
            Map.entry("uxmessentials.pwarp.bypass.whitelist", "uxmessentials.bypass.whitelist.pwarp"),
            Map.entry("uxmessentials.pwarp.bypass.cost", "uxmessentials.bypass.cost.pwarp"),
            Map.entry("uxmessentials.pwarp.bypass.safety", "uxmessentials.bypass.safety.pwarp"),
            Map.entry("uxmessentials.world.access.bypass", "uxmessentials.bypass.access.world"),
            Map.entry("uxmessentials.world.bypass.safety", "uxmessentials.bypass.safety.world"),
            Map.entry("uxmessentials.world.gamemode.bypass", "uxmessentials.bypass.gamemode.world"),
            Map.entry("uxmessentials.world.voidrescue.exempt", "uxmessentials.bypass.void-rescue"),
            Map.entry("uxmessentials.vault.bypass-blacklist", "uxmessentials.bypass.blacklist.vault"),
            Map.entry("uxmessentials.vault.free", "uxmessentials.bypass.cost.vault"),
            Map.entry("uxmessentials.economy.baltop.exempt", "uxmessentials.bypass.baltop"),
            Map.entry("uxmessentials.economy.bypasscmdcost", "uxmessentials.bypass.cost.command"),
            Map.entry("uxmessentials.economy.tax.bypass", "uxmessentials.bypass.tax"),
            Map.entry("uxmessentials.moderation.lockdown.bypass", "uxmessentials.bypass.lockdown"),
            Map.entry("uxmessentials.moderation.exempt", "uxmessentials.bypass.punishment"),
            Map.entry("uxmessentials.security.bypass", "uxmessentials.bypass.security"),
            Map.entry("uxmessentials.commandcontrol.channelhide.bypass", "uxmessentials.bypass.channel-hide"),
            Map.entry("uxmessentials.commandcontrol.spam.bypass", "uxmessentials.bypass.command-spam"),
            Map.entry("uxmessentials.commandcontrol.bypass", "uxmessentials.bypass.commandcontrol"),
            Map.entry("uxmessentials.world.command-bypass", "uxmessentials.bypass.blocked-commands.world"),
            Map.entry("uxmessentials.communication.chat.bypass", "uxmessentials.bypass.chat-lock"),
            Map.entry("uxmessentials.communication.clearchat.exempt", "uxmessentials.bypass.clear-chat"),
            Map.entry("uxmessentials.spawn.join.exempt", "uxmessentials.bypass.join-spawn"),
            Map.entry("uxmessentials.tp.warmup.bypass", "uxmessentials.bypass.warmup.tp"));

    private RenamedPermissions() {}

    /** The node {@code configured} names now: the current spelling of a renamed node, or the value unchanged. */
    public static String current(String configured) {
        Objects.requireNonNull(configured, "configured");
        String node = configured.strip();
        if (node.startsWith(LABEL_COOLDOWN_BYPASS)) {
            return SharedPermissions.cooldownBypass(node.substring(LABEL_COOLDOWN_BYPASS.length()));
        }
        return RENAMED.getOrDefault(node, node);
    }

    /**
     * Every renamed fixed node, earlier spelling to current. The per-label cooldown exemptions are a pattern instead:
     * {@code uxmessentials.cooldown.bypass.<label>} reads as {@code uxmessentials.bypass.cooldown.label.<label>}.
     */
    public static Map<String, String> all() {
        return RENAMED;
    }
}
