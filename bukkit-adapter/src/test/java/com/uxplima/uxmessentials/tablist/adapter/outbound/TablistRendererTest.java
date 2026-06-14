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

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
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
        TablistRenderer renderer = new TablistRenderer(ref::get, new AnimationRegistry(List.of()));

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

    @Test
    void anAnimationTokenInTheHeaderAndFooterRendersTheCurrentFrame() {
        // The header/footer are delivered through player.sendPlayerListHeaderAndFooter, which the stock PlayerMock
        // leaves
        // a no-op (so playerListHeader()/Footer() never update). A capturing PlayerMock records the components uxmLib's
        // Tablist hands the player, letting us assert the %anim_<name>% token resolved to the current frame in both.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "anim");
        server.addPlayer(player);
        AnimationRegistry registry = new AnimationRegistry(List.of(AnimationDef.frames(
                new AnimationSpec("blink", AnimationSpec.AnimationType.FRAMES, List.of("ON", "OFF"), 1))));
        TablistContent content = new TablistContent(
                List.of("<gray>State: %anim_blink%"),
                List.of("<gray>Foot: %anim_blink%"),
                Duration.ofSeconds(1L),
                Set.of());
        TablistFormatConfig config = new TablistFormatConfig(List.of(new TablistFormat(
                "default", DisplayCondition.always(), 0, content, Optional.empty(), OptionalInt.empty())));
        TablistRenderer renderer = new TablistRenderer(new AtomicReference<>(config)::get, registry);

        // tick 0 -> frame index 0 ("ON"); the rendered header and footer both carry the current frame.
        renderer.renderFor(player);
        assertThat(plain(player.header())).isEqualTo("State: ON");
        assertThat(plain(player.footer())).isEqualTo("Foot: ON");

        // advance once -> frame index 1 ("OFF"); both follow the shared global clock.
        registry.advance();
        renderer.renderFor(player);
        assertThat(plain(player.header())).isEqualTo("State: OFF");
        assertThat(plain(player.footer())).isEqualTo("Foot: OFF");
    }

    @Test
    void aNameOnlyFormatDoesNotBlankTheHeaderOrFooterButAppliesTheName() {
        // A format with an EMPTY header AND footer must NOT call sendPlayerListHeaderAndFooter at all — uxmLib's
        // Tablist.set sends both together, so sending an empty pair would wipe whatever vanilla or another plugin set.
        // The send count being zero is the observable proof the tab header/footer was left untouched.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "nameonly");
        server.addPlayer(player);
        TablistRenderer renderer =
                renderer(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<gold>{player}", 42))));

        renderer.renderFor(player);

        assertThat(player.sendCount()).isZero();
        // The name and order still apply — a name-only/order-only format is fully functional.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(42);
    }

    @Test
    void switchingFromAHeaderFormatToANameOnlyFormatClearsTheRenderersHeaderFooter() {
        // A player who had a header-having format and then switches to a name-only one must have THIS renderer's
        // header/footer cleared (an empty pair) rather than left stale — the second send is the clear.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "switcher");
        server.addPlayer(player);
        AtomicReference<TablistFormatConfig> ref =
                new AtomicReference<>(new TablistFormatConfig(List.of(new TablistFormat(
                        "header",
                        DisplayCondition.always(),
                        0,
                        new TablistContent(
                                List.of("<gold>Welcome"), List.of("<gray>footer"), Duration.ofSeconds(1L), Set.of()),
                        Optional.of("<gold>{player}"),
                        OptionalInt.of(5)))));
        TablistRenderer renderer = new TablistRenderer(ref::get, new AnimationRegistry(List.of()));

        renderer.renderFor(player);
        assertThat(player.sendCount()).isEqualTo(1);
        assertThat(plain(player.header())).isEqualTo("Welcome");

        // Switch to a name-only format: the renderer clears its own header/footer (sends an empty pair).
        ref.set(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<aqua>{player}", 9))));
        renderer.renderFor(player);

        assertThat(player.sendCount()).isEqualTo(2);
        assertThat(plain(player.header())).isEmpty();
        assertThat(plain(player.footer())).isEmpty();
    }

    @Test
    void aNameOnlyFormatLeavesAFreshPlayerUntouchedAcrossSteadyStateTicks() {
        // A player who never had a header/footer from this renderer keeps zero sends across repeated paints of a
        // name-only format — the renderer never blanks a tab it did not author.
        CapturingPlayerMock player = new CapturingPlayerMock(server, "steady");
        server.addPlayer(player);
        TablistRenderer renderer =
                renderer(new TablistFormatConfig(List.of(nameOnlyFormat("default", "<gold>{player}", 3))));

        renderer.renderFor(player);
        renderer.renderFor(player);

        assertThat(player.sendCount()).isZero();
    }

    /**
     * A PlayerMock that records the header/footer components handed to {@code sendPlayerListHeaderAndFooter} and counts
     * the sends. The stock PlayerMock leaves that call a no-op, so this is the only way to observe whether the renderer
     * sent a header/footer at all — the send count distinguishes "never touched" from "cleared to empty".
     */
    private static final class CapturingPlayerMock extends PlayerMock {
        private @Nullable Component lastHeader;
        private @Nullable Component lastFooter;
        private int sendCount;

        CapturingPlayerMock(ServerMock server, String name) {
            super(server, name);
        }

        @Override
        public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
            this.lastHeader = header;
            this.lastFooter = footer;
            this.sendCount++;
        }

        Component header() {
            return java.util.Objects.requireNonNull(lastHeader, "header not sent");
        }

        Component footer() {
            return java.util.Objects.requireNonNull(lastFooter, "footer not sent");
        }

        int sendCount() {
            return sendCount;
        }
    }

    private TablistRenderer renderer(TablistFormatConfig config) {
        return new TablistRenderer(new AtomicReference<>(config)::get, new AnimationRegistry(List.of()));
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

    /** A format with an EMPTY header AND footer (a name-only / order-only format) plus the given name/order. */
    private static TablistFormat nameOnlyFormat(String name, @Nullable String nameFormat, @Nullable Integer sortOrder) {
        TablistContent blank = new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                blank,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
