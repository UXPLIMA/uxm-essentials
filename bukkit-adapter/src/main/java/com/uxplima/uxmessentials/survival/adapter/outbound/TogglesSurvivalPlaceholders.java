package com.uxplima.uxmessentials.survival.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.SurvivalPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SurvivalPlaceholders} seam over the live per-player switches and the module's config view. The
 * switches are the same PDC stamps the toggle commands write, read with the same default they toggle against, so
 * a placeholder and a {@code /autopickup} reply can never disagree.
 *
 * <p>An offline player has no persistent data container to read here, so every switch reports as off for them:
 * a mechanic that fires while mining is meaningless for somebody who is not mining.
 */
@NullMarked
public final class TogglesSurvivalPlaceholders implements SurvivalPlaceholders {

    /** Every mechanic ships on, which is the default the toggle commands flip against. */
    private static final boolean DEFAULT_ON = true;

    private final Server server;
    private final PdcSurvivalToggles toggles;
    private final SurvivalConfig config;

    public TogglesSurvivalPlaceholders(Server server, PdcSurvivalToggles toggles, SurvivalConfig config) {
        this.server = Objects.requireNonNull(server, "server");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public boolean active(PlayerRef who, Mechanic mechanic) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(mechanic, "mechanic");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        return switch (mechanic) {
            case TREE_FELLER -> toggles.treeFellerActive(player, DEFAULT_ON);
            case VEINMINER -> toggles.veinminerActive(player, DEFAULT_ON);
            case FARM_PROTECT -> toggles.farmProtectActive(player, DEFAULT_ON);
            case AUTO_PICKUP -> toggles.autoPickupActive(player, DEFAULT_ON);
            case AUTO_SMELT -> toggles.autoSmeltActive(player, DEFAULT_ON);
            case AUTO_SELL -> toggles.autoSellActive(player, DEFAULT_ON);
            case AUTO_TOOL -> toggles.autoToolActive(player, DEFAULT_ON);
        };
    }

    @Override
    public boolean enabled(Mechanic mechanic) {
        Objects.requireNonNull(mechanic, "mechanic");
        return switch (mechanic) {
            case TREE_FELLER -> config.treeFeller().enabled();
            case VEINMINER -> config.veinminer().enabled();
            case FARM_PROTECT -> config.farmProtect().enabled();
            case AUTO_PICKUP -> config.autoPickup().enabled();
            case AUTO_SMELT -> config.autoSmelt().enabled();
            case AUTO_SELL -> config.autoSell().enabled();
            case AUTO_TOOL -> config.autoTool().enabled();
        };
    }
}
