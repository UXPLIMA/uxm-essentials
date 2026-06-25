package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

/**
 * MockBukkit coverage of the new-world {@link WorldEditorScreen#CREATE} screen. Opening the screen places a button per
 * draft field; cycling a selector reopens with the updated draft; clicking the create button builds the draft's
 * {@link WorldSpec} (mirroring {@code WorldCommand.runCreate}) and runs {@link CreateWorld} on the global thread; and a
 * draft with no name never reaches {@code CreateWorld}. The scheduler is synchronous so the entity-bound opens and the
 * global hop run inline, and {@link CreateWorld} is a Mockito mock so the built spec can be captured and asserted.
 */
class WorldCreateViewPathTest {

    private ServerMock server;
    private PlayerMock viewer;
    private CreateWorld createWorld;
    private WorldCreateView view;
    private int listReopens;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = server.addPlayer("Staff");
        createWorld = mock(CreateWorld.class);
        when(createWorld.create(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(Result.ok());
        WorldEditorText text = new WorldEditorText(new KeyMessages());
        WorldNotifier notifier = new WorldNotifier(new KeyMessages(), new NoopSink());
        Scheduler scheduler = new SyncScheduler();
        listReopens = 0;
        TextInput textInput = TextInputTestKit.create(
                MockBukkit.createMockPlugin(),
                new GuiText(new KeyMessages()),
                scheduler,
                Path.of("nonexistent"),
                new NoopLogger());
        view = new WorldCreateView(
                text, createWorld, notifier, (player, v) -> listReopens++, textInput, scheduler, threeRow());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void opensACreateScreenHolderCarryingAFreshDraft() {
        view.open(viewer, ref(viewer));

        InventoryHolder holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(WorldEditorHolder.class);
        assertThat(((WorldEditorHolder) holder).screen()).isEqualTo(WorldEditorScreen.CREATE);
        WorldCreateDraft draft = currentDraft();
        assertThat(draft.environment()).isEqualTo(WorldEnvironment.NORMAL);
        assertThat(draft.worldType()).isEqualTo(WorldGenType.NORMAL);
        assertThat(draft.hasName()).isFalse();
    }

    @Test
    void cyclingTheEnvironmentSelectorReopensWithTheNextEnvironment() {
        view.onClick(viewer, ref(viewer), WorldCreateDraft.empty(), WorldCreateView.ENVIRONMENT_SLOT, false);

        assertThat(currentDraft().environment()).isEqualTo(WorldEnvironment.NETHER);
    }

    @Test
    void cyclingTheGeneratorSelectorWalksVanillaVoidFlat() {
        view.onClick(viewer, ref(viewer), WorldCreateDraft.empty(), WorldCreateView.GENERATOR_SLOT, false);
        assertThat(currentDraft().generatorId()).contains(BuiltInGenerators.VOID);

        view.onClick(viewer, ref(viewer), currentDraft(), WorldCreateView.GENERATOR_SLOT, false);
        assertThat(currentDraft().generatorId()).contains(BuiltInGenerators.FLAT);

        view.onClick(viewer, ref(viewer), currentDraft(), WorldCreateView.GENERATOR_SLOT, false);
        assertThat(currentDraft().generatorId()).isEmpty();
    }

    @Test
    void clickingCreateBuildsTheSpecAndRunsCreateWorldOnTheGlobalThread() {
        WorldCreateDraft draft = WorldCreateDraft.empty()
                .withName("frontier")
                .withEnvironment(WorldEnvironment.NETHER)
                .withWorldType(WorldGenType.AMPLIFIED)
                .withGenerator(BuiltInGenerators.VOID)
                .withSeed(Optional.of(42L));

        view.onClick(viewer, ref(viewer), draft, WorldCreateView.CREATE_SLOT, false);

        ArgumentCaptor<WorldSpec> spec = ArgumentCaptor.forClass(WorldSpec.class);
        verify(createWorld).create(eq(ref(viewer)), eq(WorldName.of("frontier")), spec.capture(), eq(true));
        WorldSpec built = spec.getValue();
        assertThat(built.environment()).isEqualTo(WorldEnvironment.NETHER);
        assertThat(built.worldType()).isEqualTo(WorldGenType.AMPLIFIED);
        assertThat(built.seed()).contains(42L);
        assertThat(built.generator()).contains(BuiltInGenerators.ref(BuiltInGenerators.VOID));
        assertThat(built.generateStructures()).isTrue();
        assertThat(built.dimension()).isEmpty();
    }

    @Test
    void aVanillaNoSeedDraftBuildsTheSameSpecRunCreateWould() {
        WorldCreateDraft draft = WorldCreateDraft.empty().withName("plain");

        view.onClick(viewer, ref(viewer), draft, WorldCreateView.CREATE_SLOT, false);

        ArgumentCaptor<WorldSpec> spec = ArgumentCaptor.forClass(WorldSpec.class);
        verify(createWorld).create(any(), eq(WorldName.of("plain")), spec.capture(), eq(true));
        WorldSpec built = spec.getValue();
        // runCreate's defaults: NORMAL/NORMAL, empty seed, empty generator (vanilla), structures on, no dimension.
        assertThat(built.environment()).isEqualTo(WorldEnvironment.NORMAL);
        assertThat(built.worldType()).isEqualTo(WorldGenType.NORMAL);
        assertThat(built.seed()).isEmpty();
        assertThat(built.generator()).isEmpty();
    }

    @Test
    void clickingCreateWithNoNameNeverReachesCreateWorld() {
        view.onClick(viewer, ref(viewer), WorldCreateDraft.empty(), WorldCreateView.CREATE_SLOT, false);

        verify(createWorld, never()).create(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        // The screen reopens so the staff member can supply a name.
        WorldEditorHolder h =
                (WorldEditorHolder) viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(h.screen()).isEqualTo(WorldEditorScreen.CREATE);
    }

    @Test
    void clickingBackReturnsToTheWorldList() {
        view.onClick(viewer, ref(viewer), WorldCreateDraft.empty(), WorldCreateView.BACK_SLOT, false);

        // Back reopens the engine-rendered world picker through the reopen seam rather than a bespoke LIST holder.
        assertThat(listReopens).isEqualTo(1);
    }

    @Test
    void draftToSpecMatchesAManuallyBuiltSpec() {
        WorldSpec built = WorldCreateDraft.empty()
                .withName("x")
                .withGenerator(BuiltInGenerators.FLAT)
                .toSpec();
        Optional<GeneratorRef> flat = Optional.of(BuiltInGenerators.ref(BuiltInGenerators.FLAT));
        assertThat(built)
                .isEqualTo(new WorldSpec(
                        WorldEnvironment.NORMAL, WorldGenType.NORMAL, Optional.empty(), flat, true, Optional.empty()));
    }

    private WorldCreateDraft currentDraft() {
        WorldEditorHolder h =
                (WorldEditorHolder) viewer.getOpenInventory().getTopInventory().getHolder();
        return java.util.Objects.requireNonNull(h.draft(), "draft");
    }

    private static GuiLayout threeRow() {
        return new GuiLayout(3, Material.GRAY_STAINED_GLASS_PANE, Material.ARROW, 0, 1, List.of());
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class SyncScheduler implements Scheduler {
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
