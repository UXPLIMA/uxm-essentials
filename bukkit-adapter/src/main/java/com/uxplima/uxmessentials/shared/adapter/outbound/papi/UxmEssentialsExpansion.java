package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The older {@code %uxmessentials_<key>%} expansion, kept so placeholders written before the rename still answer. A
 * thin PlaceholderAPI shell: its {@link PlaceholderRoute} takes the key out of the parameters and names it in the log,
 * and {@link EssentialsAnswers} answers it as the shared {@code %uxm_essentials_<key>%} form does. It is constructed
 * and registered solely past the PlaceholderAPI-present guard.
 *
 * <p>It also implements PlaceholderAPI's {@code Relational} contract, for {@code %rel_uxmessentials_<key>%}.
 *
 * <p>{@link #persist()} is {@code true} so the expansion survives a PlaceholderAPI internal reload: the plugin owns
 * its lifecycle and unregisters it on its own disable. An unknown key resolves to {@code null}, the PlaceholderAPI
 * signal to leave the raw token in place.
 */
@NullMarked
public final class UxmEssentialsExpansion extends PlaceholderExpansion implements Relational {

    private final EssentialsAnswers answers;
    private final String version;
    private final PlaceholderRoute route;

    UxmEssentialsExpansion(EssentialsAnswers answers, String version, PlaceholderRoute route) {
        this.answers = Objects.requireNonNull(answers, "answers");
        this.version = Objects.requireNonNull(version, "version");
        this.route = Objects.requireNonNull(route, "route");
    }

    @Override
    public String getIdentifier() {
        return route.identifier();
    }

    @Override
    public String getAuthor() {
        return "UXPLIMA";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        Objects.requireNonNull(params, "params");
        // The key is read first so an old placeholder on a player-less surface is still named in the log.
        Optional<String> key = route.key(params);
        return key.isEmpty() ? null : answers.answer(player, key.get());
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player viewer, @Nullable Player target, String params) {
        Objects.requireNonNull(params, "params");
        Optional<String> key = route.key(params);
        return key.isEmpty() ? null : answers.relational(viewer, target, key.get());
    }
}
