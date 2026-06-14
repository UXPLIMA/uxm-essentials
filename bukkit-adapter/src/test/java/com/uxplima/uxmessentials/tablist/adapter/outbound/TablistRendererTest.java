package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
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
 * blacklisted world tears the selected format down. The skin path is covered too: a format with a {@code texture:} skin
 * delivers the row through a captured {@link TabListPackets} (with that {@link TabSkin}) rather than the native setters,
 * a no-skin format takes the native path and sends no packet, a {@code player:} skin resolves an online player's
 * texture, and the offline skin fetch is async with a no-skin fallback while in flight.
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
        TablistRenderer renderer = rendererOf(ref::get, new AnimationRegistry(List.of()), new RecordingPackets());

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
        TablistRenderer renderer = rendererOf(new AtomicReference<>(config)::get, registry, new RecordingPackets());

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
        TablistRenderer renderer = rendererOf(ref::get, new AnimationRegistry(List.of()), new RecordingPackets());

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

    @Test
    void aTextureSkinFormatPaintsTheRowThroughAPacketWithThatSkin() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("dmFsdWU=", Optional.of("sig"));
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 8, skin));

        renderer.renderFor(player);

        // The row is delivered through a packet carrying the texture, NOT the native list-name setter.
        assertThat(packets.added).hasSize(1);
        TabEntry entry = packets.added.get(0);
        assertThat(entry.id()).isEqualTo(player.getUniqueId());
        assertThat(entry.name()).isEqualTo(player.getName());
        assertThat(entry.listOrder()).isEqualTo(8);
        assertThat(plain(entry.displayName())).isEqualTo(player.getName());
        TabSkin painted = skinOf(entry);
        assertThat(painted.textureValue()).isEqualTo("dmFsdWU=");
        assertThat(painted.signature()).isEqualTo("sig");
        // The packet was broadcast to the one online viewer.
        assertThat(packets.sends).isEqualTo(1);
        // The native list name was not touched for the skinned row.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
    }

    @Test
    void aNoSkinFormatTakesTheNativePathAndSendsNoPacket() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer =
                rendererWith(packets, format("default", DisplayCondition.always(), 0, "<gold>{player}", 5));

        renderer.renderFor(player);

        assertThat(packets.added).isEmpty();
        assertThat(packets.sends).isZero();
        // The native path applied the name and order.
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(player.getPlayerListOrder()).isEqualTo(5);
    }

    @Test
    void doesNotReSendTheSkinPacketOnASteadyStateTick() {
        // Re-adding a real online player's entry every refresh tick would flicker. The packet is apply-on-change, so a
        // second identical paint sends nothing more.
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("dmFsdWU=", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 4, skin));

        renderer.renderFor(player);
        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(packets.sends).isEqualTo(1);
    }

    @Test
    void aPlayerNameSkinResolvesTheOnlinePlayersTexture() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.online.put("Target", new TabSkin("targettex", "targetsig"));
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, new InlineScheduler());
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Target");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 1, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers);

        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(skinOf(packets.added.get(0)).textureValue()).isEqualTo("targettex");
    }

    @Test
    void anOfflineSkinFetchIsAsyncAndFallsBackToTheNativePathWhileInFlight() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        FakeProfiles profiles = new FakeProfiles();
        profiles.fetchable.put("notch", new TabSkin("notchtex", null));
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Notch");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 2, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers);

        // First paint: the offline name is not cached yet, so the row falls back to the native path (no packet) and an
        // async fetch is scheduled but not yet run.
        renderer.renderFor(player);
        assertThat(packets.added).isEmpty();
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
        assertThat(scheduler.pending).hasSize(1);

        // Run the async fetch (off the tick thread); a later paint now finds the cached texture and paints the packet.
        scheduler.runAll();
        renderer.renderFor(player);

        assertThat(packets.added).hasSize(1);
        assertThat(skinOf(packets.added.get(0)).textureValue()).isEqualTo("notchtex");
    }

    @Test
    void aFailedOfflineFetchFallsBackToNoSkinAndNeverThrows() {
        PlayerMock player = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        // The fake has no fetchable entry for the name, so the resolver caches an empty result.
        FakeProfiles profiles = new FakeProfiles();
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource skin = new TablistSkinSource.PlayerName("Ghost");
        TablistRenderer renderer = new TablistRenderer(
                new AtomicReference<>(
                        new TablistFormatConfig(List.of(skinFormat("default", "<gold>{player}", 1, skin))))::get,
                new AnimationRegistry(List.of()),
                packets,
                resolver,
                server::getOnlinePlayers);

        renderer.renderFor(player);
        scheduler.runAll();
        // A second paint still finds no texture; the native path stands and nothing throws.
        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(packets.added).isEmpty();
        assertThat(plain(player.playerListName())).isEqualTo(player.getName());
    }

    @Test
    void repaintsASkinnedPlayersEntryToALateJoiner() {
        // A is skinned while alone, then B joins later. Native name/order replicate to B, but the custom-skin packet
        // does not — so the join-time repaint must re-send A's custom-skin entry to B (and only B).
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("YS10ZXg=", Optional.of("asig"));
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 6, skin));

        renderer.renderFor(a);
        // B joins after A was skinned: the steady tick would early-return for A (unchanged tuple), so without the
        // repaint B never receives A's custom skin.
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        List<TabEntry> toB = packets.entriesSentTo(b.getUniqueId());
        assertThat(toB).hasSize(1);
        TabEntry entry = toB.get(0);
        assertThat(entry.id()).isEqualTo(a.getUniqueId());
        assertThat(entry.listOrder()).isEqualTo(6);
        // The name format renders against A (the target), with the {player} token resolved to A's name.
        assertThat(plain(entry.displayName())).isEqualTo(a.getName());
        TabSkin painted = skinOf(entry);
        assertThat(painted.textureValue()).isEqualTo("YS10ZXg=");
        assertThat(painted.signature()).isEqualTo("asig");
    }

    @Test
    void aLateJoinerGetsTheirOwnSkinnedEntry() {
        // The joining viewer's own format carries a skin: renderFor paints it (broadcast to all viewers incl. self) and
        // the repaint re-sends it to self too, so a late joiner always sees their own custom skin.
        PlayerMock joiner = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("c2VsZg==", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 3, skin));

        renderer.renderFor(joiner);
        renderer.repaintSkinsFor(joiner);

        List<TabEntry> toSelf = packets.entriesSentTo(joiner.getUniqueId());
        assertThat(toSelf).isNotEmpty();
        TabEntry own = toSelf.get(toSelf.size() - 1);
        assertThat(own.id()).isEqualTo(joiner.getUniqueId());
        assertThat(skinOf(own).textureValue()).isEqualTo("c2VsZg==");
    }

    @Test
    void doesNotRepaintAnUnskinnedPlayerToALateJoiner() {
        // A is on the native (no-skin) path, so the repaint must send nothing to a late joiner — only skinned targets
        // are repainted.
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistRenderer renderer =
                rendererWith(packets, format("default", DisplayCondition.always(), 0, "<gold>{player}", 5));

        renderer.renderFor(a);
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        assertThat(packets.entriesSentTo(b.getUniqueId())).isEmpty();
    }

    @Test
    void doesNotRepaintAnOfflineSkinnedTargetToALateJoiner() {
        // A is skinned, then quits (forget drops their tracking). A late joiner must not be repainted A's entry — only
        // currently-online skinned targets are repainted.
        PlayerMock a = server.addPlayer();
        RecordingPackets packets = new RecordingPackets();
        TablistSkinSource skin = new TablistSkinSource.Texture("Z29uZQ==", Optional.empty());
        TablistRenderer renderer = rendererWith(packets, skinFormat("default", "<gold>{player}", 1, skin));

        renderer.renderFor(a);
        renderer.forget(a);
        a.disconnect();
        PlayerMock b = server.addPlayer();
        renderer.repaintSkinsFor(b);

        assertThat(packets.entriesSentTo(b.getUniqueId())).isEmpty();
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

    /**
     * A fake {@link TabListPackets} that records each built add entry and counts sends. The packet object is the entry,
     * so a recorded {@code (viewer, packet)} send pair carries the {@link TabEntry} that reached that viewer — this lets
     * a late-joiner test assert which entries a single viewer received.
     */
    private static final class RecordingPackets implements TabListPackets {
        private final List<TabEntry> added = new ArrayList<>();
        private final List<Sent> sent = new ArrayList<>();
        private int sends;

        @Override
        public Object addOrUpdate(TabEntry entry) {
            added.add(entry);
            return entry;
        }

        @Override
        public Object displayName(UUID id, Component name) {
            return name;
        }

        @Override
        public Object listOrder(UUID id, int order) {
            return order;
        }

        @Override
        public Object remove(List<UUID> ids) {
            return ids;
        }

        @Override
        public void send(org.bukkit.entity.Player viewer, Object packet) {
            sends++;
            sent.add(new Sent(viewer.getUniqueId(), packet));
        }

        /** The add entries that reached a single viewer, in send order. */
        List<TabEntry> entriesSentTo(UUID viewer) {
            List<TabEntry> entries = new ArrayList<>();
            for (Sent s : sent) {
                if (s.viewer().equals(viewer) && s.packet() instanceof TabEntry entry) {
                    entries.add(entry);
                }
            }
            return entries;
        }

        private record Sent(UUID viewer, Object packet) {}
    }

    /** A fake profile source: an online map for inline reads, a fetchable map for the "off-thread" fetch. */
    private static final class FakeProfiles implements MojangProfileSource {
        private final java.util.Map<String, TabSkin> online = new java.util.HashMap<>();
        private final java.util.Map<String, TabSkin> fetchable = new java.util.HashMap<>();

        @Override
        public Optional<TabSkin> onlineTexture(String name) {
            return Optional.ofNullable(online.get(name));
        }

        @Override
        public Optional<TabSkin> fetchTexture(String name) {
            return Optional.ofNullable(fetchable.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** A scheduler that runs every hop inline — used where the resolver's async fetch should complete immediately. */
    private static final class InlineScheduler extends NoopScheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }
    }

    /** A scheduler that queues async tasks so a test can assert the fetch was deferred, then run it explicitly. */
    private static final class DeferredScheduler extends NoopScheduler {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void async(Runnable task) {
            pending.add(task);
        }

        void runAll() {
            List<Runnable> snapshot = List.copyOf(pending);
            pending.clear();
            snapshot.forEach(Runnable::run);
        }
    }

    /** The hops the resolver does not use default to inline; only {@code async} matters for the skin fetch seam. */
    private abstract static class NoopScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(com.uxplima.uxmessentials.shared.domain.PlayerRef player, Runnable task) {
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

    private TablistRenderer renderer(TablistFormatConfig config) {
        return rendererOf(new AtomicReference<>(config)::get, new AnimationRegistry(List.of()), new RecordingPackets());
    }

    private TablistRenderer rendererWith(RecordingPackets packets, TablistFormat... formats) {
        return new TablistRenderer(
                new AtomicReference<>(new TablistFormatConfig(List.of(formats)))::get,
                new AnimationRegistry(List.of()),
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers);
    }

    private TablistRenderer rendererOf(
            java.util.function.Supplier<TablistFormatConfig> config,
            AnimationRegistry animations,
            RecordingPackets packets) {
        return new TablistRenderer(
                config,
                animations,
                packets,
                new TablistSkinResolver(new FakeProfiles(), new InlineScheduler()),
                server::getOnlinePlayers);
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

    /** A name-only format carrying a custom-skin source so the packet path is exercised. */
    private static TablistFormat skinFormat(
            String name, @Nullable String nameFormat, @Nullable Integer sortOrder, TablistSkinSource skin) {
        TablistContent blank = new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of());
        return new TablistFormat(
                name,
                DisplayCondition.always(),
                0,
                blank,
                Optional.ofNullable(nameFormat),
                sortOrder == null ? OptionalInt.empty() : OptionalInt.of(sortOrder),
                Optional.of(skin));
    }

    private static TabSkin skinOf(TabEntry entry) {
        return java.util.Objects.requireNonNull(entry.skin(), "entry skin");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
