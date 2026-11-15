package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;

import org.junit.jupiter.api.Test;

/** A fake connection line carries the player the way a real one does. */
class FakeConnectionLineTest {

    private static final UnaryOperator<String> NO_PLACEHOLDERS = UnaryOperator.identity();

    private static final Component DISPLAY =
            Component.text("Steve").hoverEvent(HoverEvent.showText(Component.text("Steve")));

    @Test
    void theShippedLineIsTheGamesOwnLineWithThePlayersDisplayName() {
        Component line = VanishConnectionMessenger.render(
                        "<yellow><lang:multiplayer.player.left:'<display_name>'></yellow>",
                        "Steve",
                        DISPLAY,
                        NO_PLACEHOLDERS,
                        "")
                .orElseThrow();

        List<TranslatableComponent> translated = translatables(line);
        assertThat(translated).hasSize(1);
        assertThat(translated.get(0).key()).isEqualTo("multiplayer.player.left");
        Component argument = translated.get(0).arguments().get(0).asComponent();
        assertThat(texts(argument)).anySatisfy(text -> {
            assertThat(text.content()).isEqualTo("Steve");
            assertThat(text.hoverEvent()).isEqualTo(DISPLAY.hoverEvent());
        });
    }

    @Test
    void anOperatorsLineFillsTheBareName() {
        Component line = VanishConnectionMessenger.render(
                        "<gray>{player} went home", "Steve", DISPLAY, NO_PLACEHOLDERS, "")
                .orElseThrow();

        assertThat(texts(line)).extracting(TextComponent::content).contains("Steve went home");
    }

    @Test
    void anOperatorsLineReadsThePlaceholdersAndThePrefix() {
        UnaryOperator<String> placeholders = source -> source.replace("%luckperms_prefix%", "[VIP] ");

        Component line = VanishConnectionMessenger.render(
                        "<prefix>%luckperms_prefix%{player} left the game", "Steve", DISPLAY, placeholders, "[server] ")
                .orElseThrow();

        assertThat(String.join(
                        "", texts(line).stream().map(TextComponent::content).toList()))
                .isEqualTo("[server] [VIP] Steve left the game");
    }

    @Test
    void everyViewerSeesTheSubjectsRankNotTheirOwn() {
        // The VIP and the member reading the line each have a rank of their own; only the subject's is ever asked for.
        UnaryOperator<String> subjectsRank = source -> source.replace("%luckperms_prefix%", "[Owner] ");
        List<VanishConnectionMessenger.Pending> viewers = List.of(
                new VanishConnectionMessenger.Pending("%luckperms_prefix%{player} left the game", ""),
                new VanishConnectionMessenger.Pending("%luckperms_prefix%{player} hat das Spiel verlassen", ""));

        List<String> lines = VanishConnectionMessenger.finish(viewers, "Steve", DISPLAY, subjectsRank).stream()
                .map(line -> String.join(
                        "",
                        texts(line.orElseThrow()).stream()
                                .map(TextComponent::content)
                                .toList()))
                .toList();

        assertThat(lines).containsExactly("[Owner] Steve left the game", "[Owner] Steve hat das Spiel verlassen");
    }

    @Test
    void aSharedLineReadsTheSubjectsPlaceholdersOnce() {
        List<String> asked = new ArrayList<>();
        UnaryOperator<String> subject = source -> {
            asked.add(source);
            return source;
        };
        VanishConnectionMessenger.Pending same = new VanishConnectionMessenger.Pending("{player} left the game", "");

        VanishConnectionMessenger.finish(List.of(same, same, same), "Steve", DISPLAY, subject);

        assertThat(asked).hasSize(1);
    }

    @Test
    void aBlankLineSendsNothing() {
        assertThat(VanishConnectionMessenger.render("", "Steve", DISPLAY, NO_PLACEHOLDERS, ""))
                .isEmpty();
    }

    private static List<TranslatableComponent> translatables(Component component) {
        List<TranslatableComponent> found = new ArrayList<>();
        if (component instanceof TranslatableComponent translatable) {
            found.add(translatable);
        }
        component.children().forEach(child -> found.addAll(translatables(child)));
        return found;
    }

    private static List<TextComponent> texts(Component component) {
        List<TextComponent> found = new ArrayList<>();
        if (component instanceof TextComponent text) {
            found.add(text);
        }
        component.children().forEach(child -> found.addAll(texts(child)));
        return found;
    }
}
