package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A value we answer goes into one of our own lines as the words it holds, and to everybody else as it is.
 * PlaceholderAPI and the shared {@code uxm} expansion are stood in for by a filler that hands the key of each
 * {@code %uxm_essentials_<key>%} to the registry we join with, the way the shared expansion asks it.
 */
class OurValuesInOurLinesTest {

    private static final String CLICK = "<click:run_command:'/op Mallory'>Excalibur</click>";
    private static final String CHAT_FORMAT = "<gray>%uxm_essentials_rank%</gray> holds %uxm_essentials_hand_name%";
    private static final Pattern OURS = Pattern.compile("%uxm_essentials_([a-z0-9_]+)%");

    private ServerMock server;
    private Player mallory;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        mallory = server.addPlayer("Mallory");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aHeldItemRenamedWithAClickShowsAsWordsInTheChatFormat() {
        BiFunction<OfflinePlayer, String, String> papi = filler(EverySeam.answering(CLICK));

        String line = PlaceholderApiSupport.markupBridge(mallory.getUniqueId(), papi)
                .apply("<gray>%uxm_essentials_player_name%</gray> holds %uxm_essentials_hand_name%");
        Component shown = MiniMessage.miniMessage().deserialize(line);

        assertThat(PlainTextComponentSerializer.plainText().serialize(shown)).endsWith("holds " + CLICK);
        assertThat(parts(shown))
                .allSatisfy(part -> assertThat(part.clickEvent()).isNull());
    }

    @Test
    void aLegacyColourCodeAPlayerChoseIsNoCodeInOurLine() {
        BiFunction<OfflinePlayer, String, String> papi = filler(EverySeam.answering("&cRed"));

        String line = PlaceholderApiSupport.markupBridge(mallory.getUniqueId(), papi)
                .apply("%uxm_essentials_presence_nickname%");

        assertThat(line).isEqualTo("＆cRed");
    }

    @Test
    void theOperatorsOwnMarkupStillStylesOurLine() {
        BiFunction<OfflinePlayer, String, String> papi = filler(EverySeam.answering("<gold>Knight</gold>"));

        String line =
                PlaceholderApiSupport.markupBridge(mallory.getUniqueId(), papi).apply(CHAT_FORMAT);
        Component shown = MiniMessage.miniMessage().deserialize(line);

        assertThat(parts(shown)).anySatisfy(part -> assertThat(part.color()).isEqualTo(NamedTextColor.GOLD));
        assertThat(PlainTextComponentSerializer.plainText().serialize(shown))
                .isEqualTo("Knight holds <gold>Knight</gold>");
    }

    @Test
    void aTextReadAsAValueAndAnotherPluginGetTheValueAsItIs() {
        BiFunction<OfflinePlayer, String, String> papi = filler(EverySeam.answering(CLICK));

        assertThat(PlaceholderApiSupport.valueBridge(mallory.getUniqueId(), papi)
                        .apply("%uxm_essentials_hand_name%"))
                .isEqualTo(CLICK);
        assertThat(registry(EverySeam.answering(CLICK)).resolve(mallory, "hand_name"))
                .isEqualTo(CLICK);
    }

    @Test
    void aKeyWeDoNotHaveIsLeftAsWritten() {
        BiFunction<OfflinePlayer, String, String> papi = filler(EverySeam.answering(CLICK));

        assertThat(registry(EverySeam.answering(CLICK)).resolve(mallory, "no_such_key_here"))
                .isNull();
        assertThat(PlaceholderApiSupport.valueBridge(mallory.getUniqueId(), papi)
                        .apply("%uxm_essentials_no_such_key_here%"))
                .isEqualTo("%uxm_essentials_no_such_key_here%");
    }

    @Test
    void aPassThatFailedLeavesTheNextReadAsItIs() {
        PlaceholderRegistry registry = registry(EverySeam.answering(CLICK));

        assertThatThrownBy(() -> MarkupPass.during(() -> {
                    throw new IllegalStateException("a placeholder broke the line");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.resolve(mallory, "hand_name")).isEqualTo(CLICK);
    }

    @Test
    void aPassInsideAPassLeavesTheOuterOneStanding() {
        PlaceholderRegistry registry = registry(EverySeam.answering(CLICK));

        String afterInner = MarkupPass.during(() -> {
            MarkupPass.during(() -> registry.resolve(mallory, "hand_name"));
            return registry.resolve(mallory, "hand_name");
        });

        assertThat(afterInner).startsWith("\\<click");
        assertThat(registry.resolve(mallory, "hand_name")).isEqualTo(CLICK);
    }

    @Test
    void theOlderFormAnswersTheSameWords() {
        UxmEssentialsExpansion older = new UxmEssentialsExpansion(
                new EssentialsAnswers(new PlaceholderResolver(EverySeam.answering(CLICK))),
                "test",
                PlaceholderRoute.legacy(new SilentLog()));

        assertThat(older.onRequest(mallory, "hand_name")).isEqualTo(CLICK);
        assertThat(MarkupPass.during(() -> older.onRequest(mallory, "hand_name")))
                .startsWith("\\<click");
    }

    /** The registry we join the shared {@code uxm} expansion with. */
    private static PlaceholderRegistry registry(PlaceholderContexts contexts) {
        return new EssentialsAnswers(new PlaceholderResolver(contexts)).registry();
    }

    /** PlaceholderAPI and the shared expansion as far as we are concerned: each key of ours handed over, in one pass. */
    private static BiFunction<OfflinePlayer, String, String> filler(PlaceholderContexts contexts) {
        PlaceholderRegistry registry = registry(contexts);
        return (player, text) -> {
            Matcher matcher = OURS.matcher(text);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String value = registry.resolve(player, matcher.group(1));
                matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
            }
            matcher.appendTail(out);
            return out.toString();
        };
    }

    private static List<Component> parts(Component line) {
        List<Component> parts = new ArrayList<>();
        line.iterator(ComponentIteratorType.DEPTH_FIRST).forEachRemaining(parts::add);
        return parts;
    }

    /** A log nobody reads. */
    private static final class SilentLog implements Logger {

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
