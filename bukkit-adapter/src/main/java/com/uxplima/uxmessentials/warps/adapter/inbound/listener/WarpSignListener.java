package com.uxplima.uxmessentials.warps.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class WarpSignListener implements Listener {

    private final WarpRepository repository;
    private final UseWarp useWarp;
    private final Permissions permissions;
    private final ConfigStore config;
    private final CommandFeedback feedback;

    public WarpSignListener(
            WarpRepository repository,
            UseWarp useWarp,
            Permissions permissions,
            ConfigStore config,
            Messages messages) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.config = Objects.requireNonNull(config, "config");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        PlayerRef ref = BukkitRefs.toRef(player);
        String header = config.getString("modules.warps.warp-sign-header", "[Warp]");

        String line0 = plain(event.line(0));
        if (!line0.equalsIgnoreCase(header)) {
            return;
        }

        // Check create permission
        if (!permissions.has(ref, "uxmessentials.warp.sign.create")) {
            feedback.send(player, WarpsMessageKey.WARP_SIGN_NO_PERMISSION_CREATE);
            event.setCancelled(true);
            return;
        }

        String warpName = plain(event.line(1));
        if (warpName.isBlank()) {
            feedback.send(player, WarpsMessageKey.WARP_SIGN_EMPTY_NAME);
            event.setCancelled(true);
            return;
        }

        // Check if warp exists
        if (!repository.exists(WarpName.of(warpName))) {
            feedback.send(player, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", warpName));
            if (config.getBoolean("modules.warps.destroy-warp-sign-on-error", true)) {
                event.getBlock().breakNaturally();
            } else {
                event.setCancelled(true);
            }
            return;
        }

        // Colorize sign header to mark it as an active warp sign.
        event.line(0, Component.text(header, NamedTextColor.DARK_BLUE));
        feedback.send(player, WarpsMessageKey.WARP_SIGN_CREATED);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        String header = config.getString("modules.warps.warp-sign-header", "[Warp]");
        org.bukkit.block.sign.SignSide front = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        // Plain text strips any colour, so the same comparison matches both a freshly typed header and the
        // dark-blue header the create handler applies to mark an active warp sign.
        String line0 = plain(front.line(0));
        if (!line0.equalsIgnoreCase(header)) {
            return;
        }

        String warpName = plain(front.line(1));
        if (warpName.isBlank()) {
            return;
        }

        Player player = event.getPlayer();
        PlayerRef ref = BukkitRefs.toRef(player);

        // Check use permission
        if (!permissions.has(ref, "uxmessentials.warp.sign.use")) {
            feedback.send(player, WarpsMessageKey.WARP_SIGN_NO_PERMISSION_USE);
            event.setCancelled(true);
            return;
        }

        // Execute teleport
        useWarp.use(ref, WarpName.of(warpName));
        event.setCancelled(true);
    }

    private static String plain(@org.jspecify.annotations.Nullable Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line);
    }
}
