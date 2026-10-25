package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMeta;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatPlaceholderExpander;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

/**
 * MockBukkit coverage of {@link ChatFormatListener}. The {@link AsyncChatEvent} is a Mockito double (the real chat
 * pipeline is not needed) with a real {@link PlayerMock} speaker so the permission check and name/world reads run;
 * the installed {@link ChatRenderer} is captured and invoked directly so the rendered line is observable. Proves a
 * formatted line reads prefix + display name + suffix + message, that a per-group format is selected by the
 * speaker's primary group, and that a {@code <red>} tag in a player's own message stays literal unless the speaker
 * is permitted to format their message.
 */
class ChatFormatListenerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String FORMAT_PERMISSION = "uxmessentials.communication.chat.format";

    private ServerMock server;
    private PlayerMock speaker;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        speaker = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFormattedLineReadsPrefixDisplayNameSuffixAndMessage() {
        ChatFormatPolicy policy = new ChatFormatPolicy(
                true, "<prefix><display_name><suffix><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("[VIP] ", " ★", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice ★: hello");
    }

    @Test
    void aPerGroupFormatIsSelectedByTheSpeakersPrimaryGroup() {
        ChatFormatPolicy policy = new ChatFormatPolicy(
                true, "<display_name>: <message>", Map.of("vip", "<display_name> [vip]: <message>"), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("", "", Optional.of("vip")));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("Alice [vip]: hello");
    }

    @Test
    void aNonPermittedPlayersMiniMessageTagStaysLiteral() {
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi <red>there"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("Alice: hi <red>there");
    }

    @Test
    void aPermittedPlayersMiniMessageTagIsParsed() {
        speaker.addAttachment(MockBukkit.createMockPlugin(), FORMAT_PERMISSION, true);
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi <red>there"), speaker);

        String plain = PLAIN.serialize(line);
        assertThat(plain).isEqualTo("Alice: hi there");
        assertThat(plain).doesNotContain("<red>");
    }

    @Test
    void aMessageAnotherPluginPaintedKeepsItsColour() {
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component painted = Component.text("hello", NamedTextColor.LIGHT_PURPLE);
        Component line = renderer.render(speaker, Component.text("Alice"), painted, speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("Alice: hello");
        assertThat(colourOf(line, "hello")).isEqualTo(NamedTextColor.LIGHT_PURPLE);
    }

    @Test
    void aPermittedPlayerWhoWritesNoTagKeepsThePaintToo() {
        speaker.addAttachment(MockBukkit.createMockPlugin(), FORMAT_PERMISSION, true);
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component painted = Component.text("hello", NamedTextColor.LIGHT_PURPLE);
        Component line = renderer.render(speaker, Component.text("Alice"), painted, speaker);

        assertThat(colourOf(line, "hello")).isEqualTo(NamedTextColor.LIGHT_PURPLE);
    }

    @Test
    void aDisabledPolicyInstallsNoRenderer() {
        ChatFormatPolicy policy = ChatFormatPolicy.disabled();
        ChatFormatListener listener = new ChatFormatListener(
                () -> policy,
                source(ChatMeta.empty()),
                ChatPlaceholderExpander.identity(),
                ChatFormatListenerTest::asWritten);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(speaker);

        listener.onChat(event);

        verify(event, never()).renderer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aLegacyColourCodedPrefixRendersInColourNotLiterally() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("&c[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        // The &c code is consumed (no literal "&c") and re-emitted as a red span, not shown verbatim.
        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aSectionSignLegacyPrefixRendersInColourToo() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("\u00A7c[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aMiniMessagePrefixIsStillParsedAsMiniMessage() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("<red>[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aPapiPlaceholderInTheFormatIsExpandedWhenTheSeamIsPresent() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "%papi_tag% <display_name>: <message>", Map.of(), false, "", "");
        ChatPlaceholderExpander present = (who, text) -> text.replace("%papi_tag%", "[STAFF]");
        ChatRenderer renderer = render(policy, ChatMeta.empty(), present);

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[STAFF] Alice: hi");
    }

    @Test
    void aPapiPlaceholderIsLeftUntouchedWhenTheSeamIsAbsent() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "%papi_tag% <display_name>: <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty()); // identity expander, no PlaceholderAPI

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("%papi_tag% Alice: hi");
    }

    @Test
    void aPrefixThatNamesACatalogueLineReadsInEachReadersOwnLanguage() {
        PlayerMock reader = server.addPlayer("Bob");
        BiFunction<UUID, String, String> lines = (viewer, line) -> {
            if (!line.equals("@custom.rank.owner")) {
                return line;
            }
            if (viewer.equals(reader.getUniqueId())) {
                return "[KURUCU] ";
            }
            return viewer.equals(PlayerRef.SYSTEM_UUID) ? "[SERVER OWNER] " : "[OWNER] ";
        };
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name>: <message>", Map.of(), false, "", "");
        ChatFormatListener listener = new ChatFormatListener(
                () -> policy,
                source(new ChatMeta("@custom.rank.owner", "", Optional.empty())),
                ChatPlaceholderExpander.identity(),
                lines);
        ChatRenderer renderer = installed(listener);

        Component forSpeaker = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);
        Component forReader = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), reader);
        Component forConsole =
                renderer.render(speaker, Component.text("Alice"), Component.text("hi"), server.getConsoleSender());

        assertThat(PLAIN.serialize(forSpeaker)).isEqualTo("[OWNER] Alice: hi");
        assertThat(PLAIN.serialize(forReader)).isEqualTo("[KURUCU] Alice: hi");
        assertThat(PLAIN.serialize(forConsole)).isEqualTo("[SERVER OWNER] Alice: hi");
    }

    @Test
    void aRelationalPlaceholderInTheFormatIsFilledForEachReader() {
        PlayerMock reader = server.addPlayer("Bob");
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "%rel_tag%<display_name>: <message>", Map.of(), false, "", "");
        ChatPlaceholderExpander relational = new ChatPlaceholderExpander() {
            @Override
            public String expand(UUID who, String text) {
                return text;
            }

            @Override
            public String expandRelational(UUID readerId, UUID speakerId, String text) {
                String tag = readerId.equals(reader.getUniqueId()) ? "[EFSANE] " : "[LEGEND] ";
                return speakerId.equals(speaker.getUniqueId()) ? text.replace("%rel_tag%", tag) : text;
            }
        };
        ChatRenderer renderer = render(policy, ChatMeta.empty(), relational);

        Component forSpeaker = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);
        Component forReader = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), reader);

        assertThat(PLAIN.serialize(forSpeaker)).isEqualTo("[LEGEND] Alice: hi");
        assertThat(PLAIN.serialize(forReader)).isEqualTo("[EFSANE] Alice: hi");
    }

    /** Run the listener with {@code policy} + {@code meta} and hand back the renderer it installed on the event. */
    private ChatRenderer render(ChatFormatPolicy policy, ChatMeta meta) {
        return render(policy, meta, ChatPlaceholderExpander.identity());
    }

    /** Run the listener with {@code policy} + {@code meta} + a specific PlaceholderAPI seam, returning the renderer. */
    private ChatRenderer render(ChatFormatPolicy policy, ChatMeta meta, ChatPlaceholderExpander expander) {
        return installed(
                new ChatFormatListener(() -> policy, source(meta), expander, ChatFormatListenerTest::asWritten));
    }

    /** Run {@code listener} on one chat line and hand back the renderer it installed on the event. */
    private ChatRenderer installed(ChatFormatListener listener) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(speaker);

        listener.onChat(event);

        ArgumentCaptor<ChatRenderer> captor = ArgumentCaptor.forClass(ChatRenderer.class);
        verify(event).renderer(captor.capture());
        return captor.getValue();
    }

    /** The colour the first run of {@code text} in {@code line} is drawn in, inherited colours included. */
    private static @Nullable TextColor colourOf(Component line, String text) {
        return colourOf(line, text, null);
    }

    private static @Nullable TextColor colourOf(Component part, String text, @Nullable TextColor inherited) {
        TextColor colour = part.color() != null ? part.color() : inherited;
        if (part instanceof TextComponent run && run.content().equals(text)) {
            return colour;
        }
        for (Component child : part.children()) {
            TextColor found = colourOf(child, text, colour);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Every affix as written, as on a server whose catalogue names none of them. */
    private static String asWritten(UUID reader, String affix) {
        return affix;
    }

    private static ChatMetaSource source(ChatMeta meta) {
        return new ChatMetaSource() {
            @Override
            public ChatMeta metaFor(UUID who) {
                return meta;
            }
        };
    }
}
