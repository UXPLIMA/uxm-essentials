package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.follow.HologramFollow;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link NametagRenderer}'s pure selection and eligible-viewer cull under MockBukkit, plus the despawn
 * bookkeeping.
 *
 * <p><strong>MockBukkit display-entity limitation.</strong> MockBukkit 4.108's {@code DisplayMock} leaves the display
 * setters unimplemented ({@code setBillboard} throws {@code UnimplementedOperationException}), so the renderer's
 * {@code spawn} path — which spawns a real {@code TextDisplay} through uxmLib — cannot run under the mock. The same
 * caveat the scoreboard number-format test calls out. So this test exercises the logic the spawn depends on directly
 * through the package-private {@link NametagRenderer#selectFor} and {@link NametagRenderer#eligibleViewers} seams:
 * format selection by permission/condition, and the viewer cull (wearer excluded, vanished-from-viewer excluded,
 * sneak hides from all). The despawn path is verified to no-op safely for an untracked player.
 */
class NametagRendererTest {

    private static final String STAFF_NODE = "uxmessentials.staff";

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selectsTheStaffFormatForAStaffWearerAndNothingForOthers() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(plugin, STAFF_NODE, true);
        PlayerMock regular = server.addPlayer();
        NametagRenderer renderer =
                renderer(singleFormat("staff", new DisplayCondition.Permission(STAFF_NODE)), alwaysVisible());

        assertThat(renderer.selectFor(staff)).map(NametagFormat::name).hasValue("staff");
        // A non-staff wearer matches no format, so they carry no nametag.
        assertThat(renderer.selectFor(regular)).isEmpty();
    }

    @Test
    void aFormatWhoseShowWhenFailsSelectsNothing() {
        PlayerMock wearer = server.addPlayer();
        // The format matches (always-true condition) but its show-when gate requires a permission the wearer lacks.
        NametagConfig config = new NametagConfig(List.of(format(
                "default",
                DisplayCondition.always(),
                new NametagVisibility(new DisplayCondition.Permission(STAFF_NODE), false, true))));
        NametagRenderer renderer = renderer(config, alwaysVisible());

        assertThat(renderer.selectFor(wearer)).isEmpty();
    }

    @Test
    void eligibleViewersExcludesTheWearerAndAViewerVanishedFromThem() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock seer = server.addPlayer();
        PlayerMock blind = server.addPlayer();
        // Vanish gate: everyone can see the wearer except `blind`.
        NametagVanish vanish = (viewer, target) -> !viewer.uuid().equals(blind.getUniqueId());
        NametagRenderer renderer = renderer(singleFormat("default", DisplayCondition.always()), vanish);
        NametagFormat format = renderer.selectFor(wearer).orElseThrow();

        List<Player> eligible = renderer.eligibleViewers(wearer, format);

        assertThat(eligible).contains(seer);
        assertThat(eligible).doesNotContain(wearer, blind);
    }

    @Test
    void hideWhileSneakingHidesTheNametagFromEveryone() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        wearer.setSneaking(true);
        NametagConfig config = new NametagConfig(List.of(format(
                "default", DisplayCondition.always(), new NametagVisibility(DisplayCondition.always(), true, true))));
        NametagRenderer renderer = renderer(config, alwaysVisible());
        NametagFormat format = renderer.selectFor(wearer).orElseThrow();

        // A sneaking wearer whose format hides on sneak shows their nametag to nobody.
        assertThat(renderer.eligibleViewers(wearer, format)).isEmpty();
    }

    @Test
    void despawnOfAnUntrackedPlayerIsASafeNoOp() {
        PlayerMock wearer = server.addPlayer();
        NametagRenderer renderer = renderer(singleFormat("default", DisplayCondition.always()), alwaysVisible());

        // Nothing was spawned, so despawn must not throw and the player stays untracked.
        assertThatCode(() -> renderer.despawn(wearer.getUniqueId())).doesNotThrowAnyException();
        assertThat(renderer.isTracked(wearer.getUniqueId())).isFalse();
    }

    private NametagRenderer renderer(NametagConfig config, NametagVanish vanish) {
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        HologramFollow follow = new HologramFollow(new PaperScheduler(plugin));
        return new NametagRenderer(
                plugin,
                () -> config,
                manager,
                follow,
                new InlineScheduler(),
                new AnimationRegistry(List.of()),
                vanish,
                Duration.ofMillis(50L));
    }

    private static NametagConfig singleFormat(String name, DisplayCondition condition) {
        return new NametagConfig(List.of(format(name, condition, NametagVisibility.alwaysVisible())));
    }

    private static NametagFormat format(String name, DisplayCondition condition, NametagVisibility visibility) {
        NametagAppearance appearance = new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3);
        return new NametagFormat(name, condition, 0, List.of("<white>{player}"), appearance, visibility);
    }

    private static NametagVanish alwaysVisible() {
        return NametagVanish.ALWAYS_VISIBLE;
    }

    /** A {@link Scheduler} that runs region/entity/global work inline so the test's hops are synchronous. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
