package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.LocaleResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A menu placeholder's value is escaped once, where it is bound, and reads the same in a line written in the menu file
 * and in a {@code @key} line: a tag a player typed shows as the words, with no backslash, and a value bound as markup is
 * styled. A trusted-markup mark is never drawn on any surface a menu or a line reaches the client through.
 */
class MenuLineEscapeTest {

    private static final String TYPED = "<click:run_command:'/op Mallory'><red>Shop";
    private static final MessageKey HEAD_NAME = () -> "gui.player-picker.head-name";
    private static final char EDGE = (char) 0xFDD1;

    @TempDir
    Path messagesDir;

    private PlayerMock viewer;
    private GuiText guiText;
    private final MenuBindings bindings = new MenuBindings();

    @BeforeEach
    void start() {
        ServerMock server = MockBukkit.mock();
        viewer = server.addPlayer("Viewer");
        guiText = guiText();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void textReadsAsTypedInAMenuLineAndInAKeyLine() {
        MenuText.text(bindings, "warp_name", ctx -> TYPED);
        String bound = bound("warp_name");

        Component written = guiText.render("<gold>" + bound + "</gold>");
        Component keyed = guiText.text(viewer, HEAD_NAME.key(), Map.of("player", bound));

        assertThat(plain(written)).isEqualTo(TYPED);
        assertThat(plain(keyed)).isEqualTo(TYPED);
        assertThat(clicks(written)).isEmpty();
        assertThat(clicks(keyed)).isEmpty();
        assertThat(colours(written)).doesNotContain(NamedTextColor.RED);
        assertThat(colours(keyed)).doesNotContain(NamedTextColor.RED);
    }

    @Test
    void markupIsStyledInAMenuLineAndInAKeyLine() {
        MenuText.markup(bindings, "rank_name", ctx -> "<red>VIP</red>");
        String bound = bound("rank_name");

        assertThat(colours(guiText.render(bound))).contains(NamedTextColor.RED);
        assertThat(colours(guiText.text(viewer, HEAD_NAME.key(), Map.of("player", bound))))
                .contains(NamedTextColor.RED);
    }

    @Test
    void aMenuArgumentAPlayerTypedIsEscapedInAKeyLine() throws IOException {
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"gui.player-picker.head-name\" = \"<gold>{argument_target}\"\n");

        Component keyed = guiText().text(viewer, HEAD_NAME.key(), argument(TYPED));

        assertThat(plain(keyed)).isEqualTo(TYPED);
        assertThat(clicks(keyed)).isEmpty();
    }

    @Test
    void aMarkIsNeverDrawnInAMenuOrALine() {
        String marked = "hi " + TrustedMarkup.of("<red>there</red>");
        PlayerRef ref = BukkitRefs.toRef(viewer);

        List<Component> drawn = new ArrayList<>();
        drawn.add(StyledText.render(marked));
        drawn.add(guiText.render(marked));
        drawn.add(guiText.text(ref, HEAD_NAME, Map.of("player", marked)));
        drawn.add(guiText.text(viewer, HEAD_NAME.key(), Map.of("player", marked)));
        drawn.add(guiText.textUnprefixed(viewer, HEAD_NAME.key(), Map.of("player", marked)));
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .name(guiText.render(marked))
                .lore(List.of(guiText.render(marked)))
                .build();
        ItemMeta meta = item.getItemMeta();
        drawn.add(meta.displayName() == null ? Component.empty() : meta.displayName());
        drawn.addAll(meta.lore() == null ? List.of() : meta.lore());

        assertThat(drawn).allSatisfy(line -> assertThat(plain(line)).doesNotContain(String.valueOf(EDGE)));
    }

    /** A catalogue over the message files as they are now, so a test can write a line of its own first. */
    private GuiText guiText() {
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(new Quiet(), messagesDir, ThemeFile::shippedTheme);
        return new GuiText(
                new CatalogMessages(catalog, new LocaleResolver(new NoChoices(), new ClientLocales(), Locale.ENGLISH)));
    }

    /** What the engine reads for {@code name} when it draws a menu. */
    private String bound(String name) {
        return bindings.placeholders()
                .resolve(name, MenuContext.of(viewer, new Object(), 0))
                .orElseThrow();
    }

    /** The engine's own values for a {@code @key} line with a menu argument, as it hands them over. */
    private static Map<String, String> argument(String typed) {
        Map<String, String> values = new HashMap<>();
        values.put("argument_target", typed);
        return values;
    }

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
