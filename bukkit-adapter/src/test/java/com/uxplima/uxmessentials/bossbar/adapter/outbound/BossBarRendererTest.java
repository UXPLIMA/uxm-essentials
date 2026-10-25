package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.bossbar.domain.BossBarWindow;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.message.CatalogLines;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.AnimationSpec.AnimationType;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.InOrder;

/** A player's bars, drawn from the written ones and kept in step with them. */
class BossBarRendererTest {

    private static final Instant NOW = Instant.parse("2026-10-25T20:00:00Z");

    private ServerMock server;
    private PlayerMock player;
    private final AtomicReference<BossBarConfig> config = new AtomicReference<>(BossBarConfig.empty());
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Map<UUID, Boolean> hidden = new HashMap<>();
    private final BossBarAnnouncements announcements = new BossBarAnnouncements();
    private BossBarRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        renderer = new BossBarRenderer(
                config::get,
                new MapStore(),
                new AnimationRegistry(List.of()),
                announcements,
                (viewer, left) -> left.toMinutes() + "m",
                now::get);
    }

    @AfterEach
    void tearDown() {
        HudText.useCatalogLines(CatalogLines.none());
        MockBukkit.unmock();
    }

    /**
     * A bar that turns through several texts is an animation whose frames are catalogue lines: each frame is read in
     * the reader's language once the animation has picked it, and the colour can turn with it.
     */
    @Test
    void aRotatingBarTurnsThroughCatalogueLinesAndColours() {
        HudText.useCatalogLines(new CatalogLines(
                (viewer, key, arguments) -> key.key().equals("custom.website") ? "<green>Website" : "<gold>Store",
                Set.of("custom.website", "custom.store")::contains));
        AnimationRegistry animations = new AnimationRegistry(List.of(
                AnimationDef.frames(new AnimationSpec(
                        "links", AnimationType.FRAMES, List.of("@custom.website", "@custom.store"), 1)),
                AnimationDef.frames(new AnimationSpec("tint", AnimationType.FRAMES, List.of("GREEN", "YELLOW"), 1))));
        BossBarRenderer rotating = new BossBarRenderer(
                config::get, new MapStore(), animations, announcements, (viewer, left) -> "", now::get);
        config.set(new BossBarConfig(List.of(bar("links", "%anim_links%", "%anim_tint%", "NOTCHED_20", "100"))));

        rotating.renderFor(player);
        BossBar shown = only(player);
        assertThat(plain(shown)).isEqualTo("Website");
        assertThat(shown.color()).isEqualTo(BossBar.Color.GREEN);

        animations.advance();
        rotating.renderFor(player);
        assertThat(plain(only(player))).isEqualTo("Store");
        assertThat(only(player).color()).isEqualTo(BossBar.Color.YELLOW);
    }

    @Test
    void aWrittenBarIsDrawnWithItsWordsColourStyleAndFill() {
        config.set(new BossBarConfig(List.of(bar("website", "<green>Website", "GREEN", "NOTCHED_20", "50"))));

        renderer.renderFor(player);

        BossBar shown = only(player);
        assertThat(plain(shown)).isEqualTo("Website");
        assertThat(shown.color()).isEqualTo(BossBar.Color.GREEN);
        assertThat(shown.overlay()).isEqualTo(BossBar.Overlay.NOTCHED_20);
        assertThat(shown.progress()).isEqualTo(0.5f);
    }

    @Test
    void aSourceThatNamesNothingFallsBackRatherThanFailing() {
        config.set(new BossBarConfig(List.of(bar("odd", "x", "MAUVE", "WAVY", "lots"))));

        renderer.renderFor(player);

        BossBar shown = only(player);
        assertThat(shown.color()).isEqualTo(BossBar.Color.WHITE);
        assertThat(shown.overlay()).isEqualTo(BossBar.Overlay.PROGRESS);
        assertThat(shown.progress()).isEqualTo(1.0f);
    }

    @Test
    void aPlayerWhoHidThemSeesNone() {
        config.set(new BossBarConfig(List.of(bar("website", "x", "GREEN", "PROGRESS", "100"))));
        renderer.renderFor(player);

        hidden.put(player.getUniqueId(), true);
        renderer.renderFor(player);

        assertThat(player.activeBossBars()).isEmpty();
    }

    @Test
    void aTimedBarDrainsCountsDownAndGoesWhenItEnds() {
        BossBarWindow window = new BossBarWindow(Optional.of(NOW), NOW.plus(Duration.ofHours(4)));
        config.set(new BossBarConfig(List.of(new BossBarDefinition(
                "event",
                "Event {remaining}",
                "GREEN",
                "PROGRESS",
                "time",
                DisplayCondition.always(),
                Optional.of(window),
                false))));

        now.set(NOW.plus(Duration.ofHours(1)));
        renderer.renderFor(player);
        BossBar shown = only(player);
        assertThat(shown.progress()).isEqualTo(0.75f);
        assertThat(plain(shown)).isEqualTo("Event 180m");

        now.set(NOW.plus(Duration.ofHours(5)));
        renderer.renderFor(player);
        assertThat(player.activeBossBars()).isEmpty();
    }

    @Test
    void aSecondDrawChangesTheSameBarInPlace() {
        config.set(new BossBarConfig(List.of(bar("website", "one", "GREEN", "PROGRESS", "100"))));
        renderer.renderFor(player);
        BossBar first = only(player);

        config.set(new BossBarConfig(List.of(bar("website", "two", "RED", "PROGRESS", "100"))));
        renderer.renderFor(player);

        assertThat(only(player)).isSameAs(first);
        assertThat(plain(first)).isEqualTo("two");
        assertThat(first.color()).isEqualTo(BossBar.Color.RED);
    }

    /** The client stacks bars in the order they were shown, so a bar that comes in above another redraws both. */
    @Test
    void aBarThatAppearsAboveAnotherKeepsTheWrittenOrder() {
        PlayerMock watched = spy(player);
        config.set(new BossBarConfig(List.of(bar("store", "Store", "GREEN", "PROGRESS", "100"))));
        renderer.renderFor(watched);
        BossBar store = only(watched);

        config.set(new BossBarConfig(List.of(
                bar("website", "Website", "GREEN", "PROGRESS", "100"),
                bar("store", "Store", "GREEN", "PROGRESS", "100"))));
        renderer.renderFor(watched);

        InOrder order = inOrder(watched);
        order.verify(watched).showBossBar(store);
        order.verify(watched).hideBossBar(store);
        order.verify(watched)
                .showBossBar(
                        org.mockito.ArgumentMatchers.argThat(bar -> plain(bar).equals("Website")));
        order.verify(watched).showBossBar(store);
        assertThat(renderer.shownIds(watched.getUniqueId())).containsExactly("website", "store");
    }

    @Test
    void anAnnouncedBarShowsForItsTimeAndCountsIt() {
        config.set(new BossBarConfig(List.of(new BossBarDefinition(
                "drop",
                "Drop {remaining}",
                "YELLOW",
                "PROGRESS",
                "time",
                DisplayCondition.always(),
                Optional.empty(),
                true))));
        renderer.renderFor(player);
        assertThat(player.activeBossBars()).isEmpty();

        announcements.announce("drop", Duration.ofMinutes(10), NOW);
        renderer.renderFor(player);

        assertThat(plain(only(player))).isEqualTo("Drop 10m");
    }

    @Test
    void aBarSentToOnePlayerReachesOnlyThem() {
        PlayerMock other = server.addPlayer();
        config.set(new BossBarConfig(List.of(new BossBarDefinition(
                "drop", "Drop", "YELLOW", "PROGRESS", "time", DisplayCondition.always(), Optional.empty(), true))));

        announcements.send(player.getUniqueId(), "drop", Duration.ofMinutes(10), NOW);
        renderer.renderFor(player);
        renderer.renderFor(other);

        assertThat(player.activeBossBars()).hasSize(1);
        assertThat(other.activeBossBars()).isEmpty();
    }

    @Test
    void theTimeLeftOnABarIsItsAnnouncementOrItsOwnEnd() {
        BossBarWindow week = new BossBarWindow(Optional.of(NOW), NOW.plus(Duration.ofDays(7)));
        config.set(new BossBarConfig(List.of(
                new BossBarDefinition(
                        "event", "x", "GREEN", "PROGRESS", "time", DisplayCondition.always(), Optional.of(week), false),
                new BossBarDefinition(
                        "drop", "x", "GREEN", "PROGRESS", "time", DisplayCondition.always(), Optional.empty(), true),
                bar("plain", "x", "GREEN", "PROGRESS", "100"))));
        announcements.send(player.getUniqueId(), "drop", Duration.ofMinutes(10), NOW);

        assertThat(renderer.remaining(player.getUniqueId(), "event")).contains(Duration.ofDays(7));
        assertThat(renderer.remaining(player.getUniqueId(), "drop")).contains(Duration.ofMinutes(10));
        assertThat(renderer.remaining(UUID.randomUUID(), "drop")).isEmpty();
        assertThat(renderer.remaining(player.getUniqueId(), "plain")).isEmpty();
        assertThat(renderer.remaining(player.getUniqueId(), "missing")).isEmpty();

        now.set(NOW.plus(Duration.ofDays(8)));
        assertThat(renderer.remaining(player.getUniqueId(), "event")).isEmpty();
    }

    @Test
    void clearTakesEveryBarAway() {
        config.set(new BossBarConfig(List.of(
                bar("website", "Website", "GREEN", "PROGRESS", "100"),
                bar("store", "Store", "GREEN", "PROGRESS", "100"))));
        renderer.renderFor(player);

        renderer.clear(player);

        assertThat(player.activeBossBars()).isEmpty();
        assertThat(renderer.shownIds(player.getUniqueId())).isEmpty();
    }

    private static BossBarDefinition bar(String id, String text, String color, String style, String progress) {
        return new BossBarDefinition(
                id, text, color, style, progress, DisplayCondition.always(), Optional.empty(), false);
    }

    private static BossBar only(PlayerMock player) {
        List<BossBar> bars = new ArrayList<>();
        player.activeBossBars().forEach(bars::add);
        assertThat(bars).hasSize(1);
        return bars.get(0);
    }

    private static String plain(BossBar bar) {
        return PlainTextComponentSerializer.plainText().serialize(bar.name());
    }

    private final class MapStore implements BossBarVisibilityStore {
        @Override
        public boolean hidden(PlayerRef who) {
            return hidden.getOrDefault(who.uuid(), false);
        }

        @Override
        public void setHidden(PlayerRef who, boolean value) {
            hidden.put(who.uuid(), value);
        }
    }
}
