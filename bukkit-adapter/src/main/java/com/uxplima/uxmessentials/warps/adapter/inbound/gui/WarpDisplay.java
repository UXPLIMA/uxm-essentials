package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The display-only projection the warp editor renders. Server warps and player warps carry the same editable
 * surface but share no Java supertype, so the editor reads either into this record and renders it once, instead
 * of duplicating the whole population method per type. The {@link Position} backs both the coordinates shown in
 * the editor lore and the destination the "go to" button reads.
 */
@NullMarked
record WarpDisplay(
        Position location,
        Optional<String> iconMaterial,
        boolean locked,
        Optional<String> password,
        List<WelcomeMessage> welcomeMessages,
        Optional<String> departureSound,
        Optional<String> arrivalSound,
        Optional<String> departureParticle,
        Optional<String> arrivalParticle,
        Optional<Double> warmupOverrideSeconds,
        Optional<Double> cooldownOverrideSeconds) {

    boolean isLocked() {
        return locked;
    }

    static WarpDisplay of(Warp w) {
        return new WarpDisplay(
                w.location(),
                w.iconMaterial(),
                w.isLocked(),
                w.password(),
                w.welcomeMessages(),
                w.departureSound(),
                w.arrivalSound(),
                w.departureParticle(),
                w.arrivalParticle(),
                w.warmupOverrideSeconds(),
                w.cooldownOverrideSeconds());
    }

    static WarpDisplay of(PlayerWarp w) {
        return new WarpDisplay(
                w.location(),
                w.iconMaterial(),
                w.isLocked(),
                w.password(),
                w.welcomeMessages(),
                w.departureSound(),
                w.arrivalSound(),
                w.departureParticle(),
                w.arrivalParticle(),
                w.warmupOverrideSeconds(),
                w.cooldownOverrideSeconds());
    }
}
