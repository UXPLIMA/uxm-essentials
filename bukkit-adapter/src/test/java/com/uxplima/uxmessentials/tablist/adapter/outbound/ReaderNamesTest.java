package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ReaderText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoAction;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoEntry;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoGameMode;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoPackets;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoState;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoTransformer;
import com.uxplima.uxmlib.packet.tablist.PlayerInfoValue;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A name in the tab list is seen by every other player, so a rank written as a catalogue line has to read in each
 * reader's language. The server holds one list name per player; these tests prove that every outbound name is
 * rewritten for the viewer it is bound for, and that a viewer who changes language is sent the names again.
 */
class ReaderNamesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private ServerMock server;
    private final Map<UUID, Locale> languages = new HashMap<>();

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
    void aHeldNameIsRewrittenIntoTheLanguageOfTheViewerItIsSentTo() {
        PlayerMock target = server.addPlayer("Owner");
        PlayerMock turk = server.addPlayer("Turk");
        PlayerMock brit = server.addPlayer("Brit");
        languages.put(turk.getUniqueId(), TURKISH);
        languages.put(brit.getUniqueId(), Locale.ENGLISH);
        PacketListenerRegistry registry = new PacketListenerRegistry();
        ReaderNames names = names(registry, null);
        names.watch(turk);
        names.watch(brit);
        names.hold(
                target.getUniqueId(),
                Component.text("[OWNER] Owner"),
                Map.of(TURKISH, Component.text("[KURUCU] Owner"), Locale.ENGLISH, Component.text("[OWNER] Owner")));

        assertThat(nameSent(registry, turk, target, Set.of(PlayerInfoAction.UPDATE_DISPLAY_NAME)))
                .isEqualTo("[KURUCU] Owner");
        assertThat(nameSent(registry, brit, target, Set.of(PlayerInfoAction.UPDATE_DISPLAY_NAME)))
                .isEqualTo("[OWNER] Owner");
    }

    @Test
    void aViewerInALanguageNobodyDrewYetReadsTheServersOwnName() {
        PlayerMock target = server.addPlayer("Owner");
        PlayerMock german = server.addPlayer("German");
        languages.put(german.getUniqueId(), Locale.GERMAN);
        PacketListenerRegistry registry = new PacketListenerRegistry();
        ReaderNames names = names(registry, null);
        names.watch(german);
        names.hold(target.getUniqueId(), Component.text("[OWNER] Owner"), Map.of(TURKISH, Component.text("[KURUCU]")));

        assertThat(nameSent(registry, german, target, Set.of(PlayerInfoAction.UPDATE_DISPLAY_NAME)))
                .isEqualTo("[OWNER] Owner");
    }

    @Test
    void aPacketThatCarriesNoNameAndAPlayerWithNoHeldNamePassUntouched() {
        PlayerMock target = server.addPlayer("Owner");
        PlayerMock other = server.addPlayer("Other");
        PlayerMock turk = server.addPlayer("Turk");
        languages.put(turk.getUniqueId(), TURKISH);
        PacketListenerRegistry registry = new PacketListenerRegistry();
        ReaderNames names = names(registry, null);
        names.watch(turk);
        names.hold(target.getUniqueId(), Component.text("[OWNER] Owner"), Map.of(TURKISH, Component.text("[KURUCU]")));

        assertThat(nameSent(registry, turk, target, Set.of(PlayerInfoAction.UPDATE_LATENCY)))
                .isEqualTo("as sent");
        assertThat(nameSent(registry, turk, other, Set.of(PlayerInfoAction.UPDATE_DISPLAY_NAME)))
                .isEqualTo("as sent");
    }

    @Test
    void aReleasedNameIsNoLongerRewritten() {
        PlayerMock target = server.addPlayer("Owner");
        PlayerMock turk = server.addPlayer("Turk");
        languages.put(turk.getUniqueId(), TURKISH);
        PacketListenerRegistry registry = new PacketListenerRegistry();
        ReaderNames names = names(registry, null);
        names.watch(turk);
        names.hold(target.getUniqueId(), Component.text("[OWNER] Owner"), Map.of(TURKISH, Component.text("[KURUCU]")));

        names.release(target.getUniqueId());

        assertThat(nameSent(registry, turk, target, Set.of(PlayerInfoAction.UPDATE_DISPLAY_NAME)))
                .isEqualTo("as sent");
    }

    @Test
    void aViewerWhoChangesLanguageIsSentEveryHeldNameInTheNewOneAndIsWatchedOnce() {
        PlayerMock target = server.addPlayer("Owner");
        PlayerMock viewer = server.addPlayer("Viewer");
        languages.put(viewer.getUniqueId(), Locale.ENGLISH);
        RecordingPackets packets = new RecordingPackets();
        RecordingGate gate = new RecordingGate();
        ReaderNames names = new ReaderNames(
                text(),
                id -> languages.getOrDefault(id, Locale.ENGLISH),
                gate,
                new PacketListenerRegistry(),
                packets,
                new QuietLogger(),
                ReaderNamesTest::applyToOneEntry);
        names.hold(
                target.getUniqueId(),
                Component.text("[OWNER] Owner"),
                Map.of(TURKISH, Component.text("[KURUCU] Owner"), Locale.ENGLISH, Component.text("[OWNER] Owner")));
        names.watch(viewer);
        packets.sent.clear();

        names.watch(viewer);
        assertThat(packets.sent).isEmpty();

        languages.put(viewer.getUniqueId(), TURKISH);
        names.watch(viewer);

        assertThat(packets.sent).hasSize(1);
        assertThat(packets.sent.get(0).viewer()).isEqualTo(viewer.getUniqueId());
        assertThat(packets.sent.get(0).names())
                .containsExactly(PlayerInfoValue.of(target.getUniqueId(), Component.text("[KURUCU] Owner")));
        assertThat(gate.injected).containsExactly(viewer.getUniqueId());
    }

    @Test
    void oneReaderStandsForEachLanguageOnline() {
        PlayerMock first = server.addPlayer("First");
        PlayerMock second = server.addPlayer("Second");
        PlayerMock third = server.addPlayer("Third");
        languages.put(first.getUniqueId(), TURKISH);
        languages.put(second.getUniqueId(), TURKISH);
        languages.put(third.getUniqueId(), Locale.ENGLISH);
        ReaderNames names = names(new PacketListenerRegistry(), null);

        Map<Locale, UUID> readers = names.readersByLanguage(List.of(first, second, third));

        assertThat(readers).containsOnlyKeys(TURKISH, Locale.ENGLISH);
        assertThat(readers.get(TURKISH)).isIn(first.getUniqueId(), second.getUniqueId());
        assertThat(readers.get(Locale.ENGLISH)).isEqualTo(third.getUniqueId());
    }

    private static ReaderText text() {
        return new ReaderText(
                line -> line.startsWith("@"),
                (reader, line) -> line,
                id -> ReaderText.Affix.NONE,
                (reader, target) -> UnaryOperator.identity());
    }

    private ReaderNames names(PacketListenerRegistry registry, @Nullable PlayerInfoPackets packets) {
        return new ReaderNames(
                text(),
                id -> languages.getOrDefault(id, Locale.ENGLISH),
                new RecordingGate(),
                registry,
                packets,
                new QuietLogger(),
                ReaderNamesTest::applyToOneEntry);
    }

    /**
     * What reaches {@code viewer} when the server sends {@code target}'s entry with {@code actions}: the name the
     * listener wrote, or "as sent" when it let the packet through.
     */
    private static String nameSent(
            PacketListenerRegistry registry, Player viewer, Player target, Set<PlayerInfoAction> actions) {
        assertThat(registry.snapshot()).hasSize(1);
        PacketListener listener = registry.snapshot().get(0);
        PlayerInfoState state = new PlayerInfoState(
                target.getUniqueId(), true, 0, PlayerInfoGameMode.SURVIVAL, Component.text("as sent"), true, 0);
        PacketVerdict verdict = listener.onSend(viewer.getUniqueId(), new OneEntry(state, actions));
        Object replacement = verdict.replacement();
        if (replacement == null) {
            return "as sent";
        }
        Component name = ((PlayerInfoState) replacement).displayName();
        return name == null ? "" : PLAIN.serialize(name);
    }

    /** The NMS-free stand-in for the uxmLib rewrite: one entry, transformed, or null when nothing changed. */
    private static @Nullable Object applyToOneEntry(Object packet, PlayerInfoTransformer t) {
        if (!(packet instanceof OneEntry entry)) {
            return null;
        }
        PlayerInfoState out = t.transform(entry.state(), entry.actions());
        return out.equals(entry.state()) ? null : out;
    }

    private record OneEntry(PlayerInfoState state, Set<PlayerInfoAction> actions) {}

    private static final class RecordingGate implements ReaderNames.Gate {
        private final List<UUID> injected = new ArrayList<>();

        @Override
        public boolean inject(Player viewer) {
            injected.add(viewer.getUniqueId());
            return true;
        }

        @Override
        public boolean eject(Player viewer) {
            return injected.remove(viewer.getUniqueId());
        }
    }

    private static final class RecordingPackets implements PlayerInfoPackets {
        private final List<Sent> sent = new ArrayList<>();

        @Override
        public Object addOrUpdate(List<PlayerInfoEntry> entries) {
            return entries;
        }

        @Override
        public Object displayNames(List<PlayerInfoValue<Component>> entries) {
            return List.copyOf(entries);
        }

        @Override
        public Object listOrders(List<PlayerInfoValue<Integer>> entries) {
            return entries;
        }

        @Override
        public Object listed(List<PlayerInfoValue<Boolean>> entries) {
            return entries;
        }

        @Override
        public Object latencies(List<PlayerInfoValue<Integer>> entries) {
            return entries;
        }

        @Override
        public Object gameModes(List<PlayerInfoValue<PlayerInfoGameMode>> entries) {
            return entries;
        }

        @Override
        public Object showHat(List<PlayerInfoValue<Boolean>> entries) {
            return entries;
        }

        @Override
        public Object removeEntries(List<UUID> ids) {
            return ids;
        }

        @Override
        @SuppressWarnings("unchecked") // displayNames above is the only builder whose packet this test sends
        public void sendPacket(Player viewer, Object packet) {
            sent.add(new Sent(viewer.getUniqueId(), (List<PlayerInfoValue<Component>>) packet));
        }

        private record Sent(UUID viewer, List<PlayerInfoValue<Component>> names) {}
    }

    private static final class QuietLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
