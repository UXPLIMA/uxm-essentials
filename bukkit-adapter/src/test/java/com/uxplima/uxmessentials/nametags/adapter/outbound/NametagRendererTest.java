package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Transform;
import com.uxplima.uxmlib.hologram.follow.HologramFollow;
import com.uxplima.uxmlib.hologram.follow.SelfHiddenNameplate;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
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
 * format selection by permission/condition, and the viewer cull (wearer excluded, vanish honoured only when the format
 * respects it, sneak hides from all, the viewer-distance block radius). The spawn-when-tracked guard is exercised via
 * the {@link NametagRenderer#trackForTest} seed seam, and the despawn path is verified to no-op safely for an untracked
 * player.
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
                new NametagVisibility(
                        new DisplayCondition.Permission(STAFF_NODE), false, true, OptionalDouble.empty()))));
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
                "default",
                DisplayCondition.always(),
                new NametagVisibility(DisplayCondition.always(), true, true, OptionalDouble.empty()))));
        NametagRenderer renderer = renderer(config, alwaysVisible());
        NametagFormat format = renderer.selectFor(wearer).orElseThrow();

        // A sneaking wearer whose format hides on sneak shows their nametag to nobody.
        assertThat(renderer.eligibleViewers(wearer, format)).isEmpty();
    }

    @Test
    void respectVanishFalseKeepsAVanishedViewerEligible() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock blind = server.addPlayer();
        // The viewer cannot see the wearer, but the format does NOT respect vanish, so they stay a candidate.
        NametagVanish vanish = (viewer, target) -> !viewer.uuid().equals(blind.getUniqueId());
        NametagConfig config = new NametagConfig(List.of(format(
                "default",
                DisplayCondition.always(),
                new NametagVisibility(DisplayCondition.always(), false, false, OptionalDouble.empty()))));
        NametagRenderer renderer = renderer(config, vanish);
        NametagFormat selected = renderer.selectFor(wearer).orElseThrow();

        assertThat(renderer.eligibleViewers(wearer, selected)).contains(blind);
    }

    @Test
    void respectVanishTrueExcludesAVanishedViewer() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock blind = server.addPlayer();
        NametagVanish vanish = (viewer, target) -> !viewer.uuid().equals(blind.getUniqueId());
        // respect-vanish defaults to true.
        NametagRenderer renderer = renderer(singleFormat("default", DisplayCondition.always()), vanish);
        NametagFormat selected = renderer.selectFor(wearer).orElseThrow();

        assertThat(renderer.eligibleViewers(wearer, selected)).doesNotContain(blind);
    }

    @Test
    void theCullUsesViewerDistanceNotTheAppearanceViewRange() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock near = server.addPlayer();
        PlayerMock far = server.addPlayer();
        near.teleport(wearer.getLocation().clone().add(5, 0, 0));
        far.teleport(wearer.getLocation().clone().add(40, 0, 0));
        // appearance view-range is a tiny multiplier (1.0) that must NOT cull anyone; the 10-block viewer-distance
        // does.
        NametagConfig config = new NametagConfig(List.of(formatWith(
                appearance(OptionalDouble.of(1.0)),
                new NametagVisibility(DisplayCondition.always(), false, true, OptionalDouble.of(10.0)))));
        NametagRenderer renderer = renderer(config, alwaysVisible());
        NametagFormat selected = renderer.selectFor(wearer).orElseThrow();

        List<Player> eligible = renderer.eligibleViewers(wearer, selected);

        assertThat(eligible).contains(near);
        assertThat(eligible).doesNotContain(far);
    }

    @Test
    void aZeroViewerDistanceShowsToEveryOnlineViewer() {
        PlayerMock wearer = server.addPlayer();
        PlayerMock far = server.addPlayer();
        far.teleport(wearer.getLocation().clone().add(500, 0, 0));
        NametagConfig config = new NametagConfig(List.of(format(
                "default",
                DisplayCondition.always(),
                new NametagVisibility(DisplayCondition.always(), false, true, OptionalDouble.of(0.0)))));
        NametagRenderer renderer = renderer(config, alwaysVisible());
        NametagFormat selected = renderer.selectFor(wearer).orElseThrow();

        // A 0 radius disables the distance cull, so even a far-away viewer is eligible.
        assertThat(renderer.eligibleViewers(wearer, selected)).contains(far);
    }

    @Test
    void spawnForAnAlreadyTrackedWearerReconcilesInsteadOfTrackingAgain() {
        PlayerMock wearer = server.addPlayer();
        server.addPlayer();
        NametagFormat format = format("default", DisplayCondition.always(), NametagVisibility.alwaysVisible());
        NametagRenderer renderer = renderer(new NametagConfig(List.of(format)), alwaysVisible());
        // Seed the wearer as already tracked with a fake hologram (MockBukkit cannot spawn a real TextDisplay). A
        // second
        // spawn for the same wearer must route through update on the same format — a text/viewer refresh, not a second
        // tracked entry that would orphan the live display and leak its follow task.
        RecordingTaskHandle followHandle = new RecordingTaskHandle();
        RecordingHologram hologram = new RecordingHologram();
        SelfHiddenNameplate nameplate = new SelfHiddenNameplate(hologram, wearer);
        renderer.trackForTest(
                wearer, hologram, nameplate, followHandle, BukkitRefs.toPosition(wearer.getLocation()), format);

        renderer.spawn(wearer, 0L);

        // Still exactly one tracked entry, the follow handle was not cancelled (no respawn), and the same-format update
        // path ran (the text was refreshed in place).
        assertThat(renderer.trackedCount()).isEqualTo(1);
        assertThat(renderer.isTracked(wearer.getUniqueId())).isTrue();
        assertThat(followHandle.cancelled).isFalse();
        assertThat(hologram.textSet).isTrue();
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
        return new NametagFormat(
                name, condition, 0, List.of("<white>{player}"), appearance(OptionalDouble.empty()), visibility);
    }

    private static NametagFormat formatWith(NametagAppearance appearance, NametagVisibility visibility) {
        return new NametagFormat(
                "default", DisplayCondition.always(), 0, List.of("<white>{player}"), appearance, visibility);
    }

    private static NametagAppearance appearance(OptionalDouble viewRange) {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                viewRange,
                1.0,
                0.3);
    }

    private static NametagVanish alwaysVisible() {
        return NametagVanish.ALWAYS_VISIBLE;
    }

    /** A {@link Hologram} that records the calls the renderer makes so the spawn-guard test can assert reconciliation. */
    private static final class RecordingHologram implements Hologram {
        private boolean textSet;

        @Override
        public void setText(Component text) {
            textSet = true;
        }

        @Override
        public void moveTo(Location to, int interpolationTicks) {}

        @Override
        public void setTransform(Transform transform) {}

        @Override
        public boolean attachTo(org.bukkit.entity.Entity target) {
            return false;
        }

        @Override
        public void restrictToViewers() {}

        @Override
        public void show(Plugin plugin, Player viewer) {}

        @Override
        public void hide(Plugin plugin, Player viewer) {}

        @Override
        public boolean isVisibleTo(Player viewer) {
            return false;
        }

        @Override
        public void forgetViewer(UUID viewer) {}

        @Override
        public void remove() {}

        @Override
        public TextDisplay entity() {
            throw new UnsupportedOperationException("no backing entity in the test fake");
        }
    }

    /** A {@link TaskHandle} that records cancellation so the spawn-guard test can assert no follow leak on reconcile. */
    private static final class RecordingTaskHandle implements TaskHandle {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
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
