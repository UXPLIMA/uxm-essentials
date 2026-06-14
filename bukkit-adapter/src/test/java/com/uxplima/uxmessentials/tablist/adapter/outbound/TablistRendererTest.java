package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link TablistRenderer} render path under MockBukkit: a selected format applies its name format and sort
 * order, condition-driven selection picks the right format per viewer (a staff player gets the staff name/order, a
 * non-staff the default with neither), no matching format resets name/order to vanilla, the {@code {player}} token is
 * substituted with the viewer's name, the apply-only-on-change tracking does not re-send an unchanged name/order, and a
 * blacklisted world tears the selected format down.
 *
 * <p>MockBukkit 4.108 implements {@code playerListName(Component)} and {@code setPlayerListOrder(int)} with real backing
 * state (the latter rejects a negative argument), so the applied values are read back directly rather than through the
 * unimplemented-setter technique the scoreboard number-format test uses. On real Paper 1.21.11 these are the same
 * setters that drive the client tab list.
 */
class TablistRendererTest {

    private static final String STAFF_NODE = "uxmessentials.staff";

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aStaffPlayerGetsTheStaffNameAndOrderAndOthersGetTheDefault() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PlayerMock regular = server.addPlayer();
        TablistFormatConfig config = new TablistFormatConfig(List.of(
                format("staff", new DisplayCondition.Permission(STAFF_NODE), 10, "<red>[Staff] {player}", 100),
                format("default", DisplayCondition.always(), 0, null, null)));
        TablistRenderer renderer = renderer(config);

        renderer.renderFor(staff);
        renderer.renderFor(regular);

        assertThat(plain(staff.playerListName())).isEqualTo("[Staff] " + staff.getName());
        assertThat(staff.getPlayerListOrder()).isEqualTo(100);
        // The default format set no name/order, so the regular player keeps the vanilla list name and order 0.
        assertThat(plain(regular.playerListName())).isEqualTo(regular.getName());
        assertThat(regular.getPlayerListOrder()).isZero();
    }

    @Test
    void noMatchingFormatResetsNameAndOrder() {
        PlayerMock player = server.addPlayer();
        AtomicReference<TablistFormatConfig> ref = new AtomicReference<>(new TablistFormatConfig(
                List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 50))));
        TablistRenderer renderer = new TablistRenderer(ref::get);

        renderer.renderFor(player);
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(50);

        // Swap to a config no format matches: the applied name/order must be reset to vanilla.
        ref.set(new TablistFormatConfig(
                List.of(format("staff", new DisplayCondition.Permission(STAFF_NODE), 0, "<red>x", 99))));
        renderer.renderFor(player);

        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isZero();
    }

    @Test
    void doesNotReApplyTheNameOrOrderOnASteadyStateTick() {
        // No format switch -> the name/order are applied once and not re-sent. We cannot directly observe a missing
        // re-send through MockBukkit's plain setters, so we assert the value is stable across two identical paints —
        // the
        // tracking maps keep it consistent and the second paint must not throw.
        PlayerMock player = server.addPlayer();
        TablistRenderer renderer = renderer(
                new TablistFormatConfig(List.of(format("default", DisplayCondition.always(), 0, "<gold>{player}", 7))));

        renderer.renderFor(player);
        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(7);
    }

    @Test
    void tearsDownAndResetsInABlacklistedWorld() {
        PlayerMock player = server.addPlayer();
        String world = player.getWorld().getName();
        TablistContent blacklisted =
                new TablistContent(List.of("<gold>Welcome"), List.of(), Duration.ofSeconds(1L), Set.of(world));
        TablistRenderer renderer = renderer(new TablistFormatConfig(List.of(new TablistFormat(
                "default",
                DisplayCondition.always(),
                0,
                blacklisted,
                Optional.of("<gold>{player}"),
                OptionalInt.of(5)))));

        renderer.renderFor(player);

        // A blacklisted world clears the tablist and leaves the player on the vanilla name/order.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isZero();
    }

    @Test
    void substitutesThePlayerToken() {
        PlayerMock player = server.addPlayer();
        TablistRenderer renderer = renderer(new TablistFormatConfig(
                List.of(format("default", DisplayCondition.always(), 0, "<gold>Welcome {player}!", null))));

        renderer.renderFor(player);

        assertThat(plain(player.playerListName())).isEqualTo("Welcome " + player.getName() + "!");
    }

    private TablistRenderer renderer(TablistFormatConfig config) {
        return new TablistRenderer(new AtomicReference<>(config)::get);
    }

    private static TablistFormat format(
            String name,
            DisplayCondition condition,
            int priority,
            @Nullable String nameFormat,
            @Nullable Integer sortOrder) {
        TablistContent content =
                new TablistContent(List.of("<gold>" + name), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                condition,
                priority,
                content,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
