package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import org.junit.jupiter.api.Test;

/**
 * The death token binding {@link ConnectionPlaceholders#death} produces from a live player and the classified
 * cause. It pins that {@code {cause}} resolves to the lowercased cause key alongside the existing {@code {killer}}
 * and {@code {killer_weapon}} tokens, so an operator death template may read the cause even under the default
 * policy. The player is a Mockito double so the name/display-name/world reads run without a live server.
 */
class ConnectionPlaceholdersTest {

    @Test
    void theCauseTokenResolvesToTheLowercasedCauseKey() {
        PlaceholderBindings bindings =
                ConnectionPlaceholders.death(player(), 3, DeathCause.FALL, "Alice hit the ground", "", "");

        assertThat(bindings.apply("{player} died from {cause}")).isEqualTo("Alice died from fall");
    }

    @Test
    void theCauseTokenSitsAlongsideTheKillerTokens() {
        PlaceholderBindings bindings =
                ConnectionPlaceholders.death(player(), 3, DeathCause.PVP, "Alice was slain", "Bob", "Diamond Sword");

        assertThat(bindings.apply("{cause}: {player} by {killer} with {killer_weapon}"))
                .isEqualTo("pvp: Alice by Bob with Diamond Sword");
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.displayName()).thenReturn(Component.text("Alice"));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);
        return player;
    }
}
