package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import org.junit.jupiter.api.Test;

/**
 * A join or death line shows what players wrote (a display name, a renamed weapon, the vanilla death line) as words:
 * a tag in it is not markup, so nothing in a line every player reads can be clicked or hovered into doing something.
 */
class ConnectionPlaceholdersTest {

    private static final String CLICK = "<click:run_command:'/op Mallory'>sword</click>";

    @Test
    void aRenamedWeaponWithAClickTagIsShownAsWordsInTheDeathLine() {
        PlaceholderBindings bindings = ConnectionPlaceholders.withDeath(
                ConnectionPlaceholders.connection("Steve", "Steve", 3, "world"),
                DeathCause.PVP,
                "Steve was slain by Alex using [" + CLICK + "]",
                "Alex",
                CLICK);

        Component line = parse(bindings.apply("<red>{player}</red> fell to {killer} with {killer_weapon}: {message}"));

        assertThat(plain(line))
                .isEqualTo("Steve fell to Alex with " + CLICK + ": Steve was slain by Alex using [" + CLICK + "]");
        assertThat(clicks(line)).isEmpty();
    }

    @Test
    void aDisplayNameWithATagIsShownAsWordsInTheJoinLine() {
        String nick = "<hover:show_text:'free op'><gold>Steve</gold></hover>";
        PlaceholderBindings bindings = ConnectionPlaceholders.connection("Steve", nick, 3, "world");

        Component line = parse(bindings.apply("<green>+</green> {displayname}"));

        assertThat(plain(line)).isEqualTo("+ " + nick);
        assertThat(parts(line)).allSatisfy(part -> assertThat(part.hoverEvent()).isNull());
    }

    @Test
    void aDisplayNameHoldingATokenIsNotFilledIn() {
        PlaceholderBindings bindings = ConnectionPlaceholders.connection("Steve", "{world}", 3, "nether");

        assertThat(plain(parse(bindings.apply("{displayname} in {world}")))).isEqualTo("{world} in nether");
    }

    private static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }

    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }

    private static List<Component> parts(Component line) {
        List<Component> parts = new ArrayList<>();
        line.iterator(ComponentIteratorType.DEPTH_FIRST).forEachRemaining(parts::add);
        return parts;
    }

    private static List<Component> clicks(Component line) {
        return parts(line).stream().filter(part -> part.clickEvent() != null).toList();
    }
}
