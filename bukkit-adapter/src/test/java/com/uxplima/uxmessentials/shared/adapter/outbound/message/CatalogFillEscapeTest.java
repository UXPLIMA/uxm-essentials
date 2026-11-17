package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A value filled into a catalogue line reads as it was written, escaped exactly once, and only a value the plugin
 * marked as markup is styled. The mark cannot be typed, and it never shows in a line.
 */
class CatalogFillEscapeTest {

    private static final String CLICK = "<click:run_command:'/op Mallory'>";
    private static final char EDGE = (char) 0xFDD1;

    private final PlayerRef reader = new PlayerRef(UUID.randomUUID(), "Reader");

    @TempDir
    Path messagesDir;

    @Test
    void aWarpDisplayNameWithTagsReadsAsTyped() {
        String name = CLICK + "<red>Shop</red></click>";

        Component line = draw(PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET, Map.of("warp", name));

        assertThat(plain(line)).contains(name).doesNotContain("\\");
        assertThat(clicks(line)).isEmpty();
        assertThat(colours(line)).doesNotContain(NamedTextColor.RED);
    }

    @Test
    void anItemNameWithTagsReadsAsTyped() {
        String name = CLICK + "<gold>Excalibur";

        Component line = draw(
                ItemworldMessageKey.ITEMINFO_LINE,
                Map.of(
                        "item", "minecraft:diamond_sword",
                        "amount", "1",
                        "name", name,
                        "enchants", "-",
                        "model", "-",
                        "durability", "-"));

        assertThat(plain(line)).contains(": " + name + " ").doesNotContain("\\");
        assertThat(clicks(line)).isEmpty();
    }

    @Test
    void aBackslashInAValueIsShownOnce() {
        Component line = draw(PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET, Map.of("warp", "C:\\shop \\<b>"));

        assertThat(plain(line)).contains("C:\\shop \\<b>");
    }

    @Test
    void aValueMarkedAsMarkupIsStyled() {
        Component line =
                draw(PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET, Map.of("warp", TrustedMarkup.of("<red>Shop</red>")));

        assertThat(plain(line)).contains(" Shop ").doesNotContain("<red>");
        assertThat(colours(line)).contains(NamedTextColor.RED);
    }

    @Test
    void aValueCannotPassItselfOffAsMarkup() {
        String earlierRun = EDGE + HexFormat.of().formatHex(new byte[16]) + EDGE;

        String forged = messages()
                .resolve(reader, PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET, Map.of("warp", earlierRun + "<red>x"));
        String stray = messages()
                .resolve(reader, PlayerwarpsMessageKey.PWARP_DISPLAY_NAME_SET, Map.of("warp", EDGE + "<red>y"));

        assertThat(forged).doesNotContain(String.valueOf(EDGE));
        assertThat(stray).doesNotContain(String.valueOf(EDGE));
        assertThat(plain(StyledText.render(forged))).contains("<red>x");
        assertThat(plain(StyledText.render(stray))).contains("<red>y");
    }

    @Test
    void aResolvedLineNeverCarriesTheMark() {
        String line = messages()
                .resolve(
                        reader,
                        PresenceMessageKey.AFK_BROADCAST_AWAY_REASON,
                        Map.of("player", "Alex", "reason", TrustedMarkup.of("<red>brb")));

        assertThat(line).doesNotContain(String.valueOf(EDGE));
    }

    @Test
    void aMarkLeftOnAnyTextIsNeverDrawn() {
        Component line = StyledText.render("hi " + TrustedMarkup.of("<red>there"));

        assertThat(plain(line)).isEqualTo("hi there");
    }

    @Test
    void anAwayReasonWithTagsReadsAsTyped() {
        String reason = CLICK + "brb</click>";

        Component line =
                draw(PresenceMessageKey.AFK_BROADCAST_AWAY_REASON, Map.of("player", "Mallory", "reason", reason));
        Component reply = draw(MessagingMessageKey.MSG_TARGET_AFK, Map.of("player", "Mallory", "reason", reason));

        assertThat(plain(line)).contains(reason).doesNotContain("\\");
        assertThat(plain(reply)).contains(reason).doesNotContain("\\");
        assertThat(clicks(line)).isEmpty();
        assertThat(clicks(reply)).isEmpty();
    }

    @Test
    void aTaggedValueInAClickIsCarriedExactly() throws IOException {
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"msg.received\" = \"<click:suggest_command:'/msg {player} {message}'>{player}</click>\"\n");

        String typed = "<3 \\o/ it's \"fine\"";

        Component line = draw(MessagingMessageKey.MSG_RECEIVED, Map.of("player", "Alex", "message", typed));

        assertThat(clicks(line)).containsExactly("/msg Alex " + typed);
    }

    @Test
    void aValueCannotLeaveTheClickItSitsIn() throws IOException {
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"msg.received\" = \"<click:suggest_command:'/msg {player} {message}'>{player}</click>\"\n");
        String typed = "x'>" + CLICK + "free</click>";

        Component line = draw(MessagingMessageKey.MSG_RECEIVED, Map.of("player", "Alex", "message", typed));

        assertThat(clicks(line)).containsExactly("/msg Alex " + typed);
    }

    private Component draw(com.uxplima.uxmessentials.shared.domain.message.MessageKey key, Map<String, String> values) {
        return StyledText.render(messages().resolve(reader, key, values));
    }

    private CatalogMessages messages() {
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(new Quiet(), messagesDir, ThemeFile::shippedTheme);
        return new CatalogMessages(catalog, new LocaleResolver(new NoChoices(), new ClientLocales(), Locale.ENGLISH));
    }

    /** The line as text, with the invisible break that keeps a value's percent from PlaceholderAPI set aside. */
    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line).replace("\u200C", "");
    }

    private static List<String> clicks(Component line) {
        List<String> found = new ArrayList<>();
        ClickEvent<?> click = line.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            found.add(text.value());
        }
        line.children().forEach(child -> found.addAll(clicks(child)));
        return found;
    }

    private static List<Object> colours(Component line) {
        List<Object> found = new ArrayList<>();
        if (line.color() != null) {
            found.add(line.color());
        }
        line.children().forEach(child -> found.addAll(colours(child)));
        return found;
    }

    private static final class NoChoices implements LocaleStore {

        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }

    private static final class Quiet implements Logger {

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
