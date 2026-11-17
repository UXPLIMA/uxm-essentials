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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A value our expansion answers goes into one of our own lines as the words it holds, and to everybody else as it is.
 * PlaceholderAPI is stood in for by a filler that hands each {@code %uxm_essentials_<key>%} to the expansion, the way
 * PlaceholderAPI calls it.
 */
class OurValuesInOurLinesTest {

    private static final String CLICK = "<click:run_command:'/op Mallory'>Excalibur</click>";
    private static final String CHAT_FORMAT = "<gray>%uxm_essentials_rank%</gray> holds %uxm_essentials_hand_name%";
    private static final Pattern OURS = Pattern.compile("%uxm_(essentials_[a-z0-9_]+)%");

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
        assertThat(expansion(EverySeam.answering(CLICK)).onRequest(mallory, "essentials_hand_name"))
                .isEqualTo(CLICK);
    }

    @Test
    void aPassThatFailedLeavesTheNextReadAsItIs() {
        UxmEssentialsExpansion expansion = expansion(EverySeam.answering(CLICK));

        assertThatThrownBy(() -> MarkupPass.during(() -> {
                    throw new IllegalStateException("a placeholder broke the line");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(expansion.onRequest(mallory, "essentials_hand_name")).isEqualTo(CLICK);
    }

    @Test
    void aPassInsideAPassLeavesTheOuterOneStanding() {
        UxmEssentialsExpansion expansion = expansion(EverySeam.answering(CLICK));

        String afterInner = MarkupPass.during(() -> {
            MarkupPass.during(() -> expansion.onRequest(mallory, "essentials_hand_name"));
            return expansion.onRequest(mallory, "essentials_hand_name");
        });

        assertThat(afterInner).startsWith("\\<click");
        assertThat(expansion.onRequest(mallory, "essentials_hand_name")).isEqualTo(CLICK);
    }

    private static UxmEssentialsExpansion expansion(PlaceholderContexts contexts) {
        return new UxmEssentialsExpansion(new PlaceholderResolver(contexts), "test", PlaceholderRoute.current());
    }

    /** PlaceholderAPI as far as our expansion is concerned: each of our placeholders handed to it, in one pass. */
    private static BiFunction<OfflinePlayer, String, String> filler(PlaceholderContexts contexts) {
        UxmEssentialsExpansion expansion = expansion(contexts);
        return (player, text) -> {
            Matcher matcher = OURS.matcher(text);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String value = expansion.onRequest(player, matcher.group(1));
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
}
