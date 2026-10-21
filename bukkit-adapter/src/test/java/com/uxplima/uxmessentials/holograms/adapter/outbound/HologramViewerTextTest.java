package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link HologramViewerText}: a hologram's words as one viewer reads them. A line with a {@code %...%} token,
 * a built-in token, an {@code @key} line or a second page marks the hologram as per-viewer, and a hologram with
 * none of those reads the same for everybody. Each viewer's text carries <em>their</em> resolved values (the
 * injected bridge factory returns a viewer-specific transform), their page and their language.
 */
class HologramViewerTextTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void detectsPerViewerTextOnlyWhenALineHasAPlaceholder() {
        HologramViewerText text = viewerText(perViewer(Map.of()));

        assertThat(text.hasPerViewerText(text("plain", "no tokens here"))).isFalse();
        assertThat(text.hasPerViewerText(text("dyn", "hi %player_name%"))).isTrue();
    }

    @Test
    void itemAndBlockHologramsAreNeverPerViewerEvenIfALabelHasAToken() {
        // An item/block hologram draws the model only, so a label line is not a per-viewer text target.
        HologramViewerText text = viewerText(perViewer(Map.of()));
        Hologram item = Hologram.createItem(name("torch"), at(), "TORCH", Instant.EPOCH);

        assertThat(text.hasPerViewerText(item)).isFalse();
    }

    @Test
    void eachViewerReadsTheirOwnResolvedText() {
        HologramViewerText text = viewerText(perViewer(Map.of(
                ALICE, source -> source.replace("%name%", "Alice"),
                BOB, source -> source.replace("%name%", "Bob"))));
        Hologram hologram = text("welcome", "hello %name%");

        assertThat(plain(text.textFor(player(ALICE), hologram, 0))).isEqualTo("hello Alice");
        assertThat(plain(text.textFor(player(BOB), hologram, 0))).isEqualTo("hello Bob");
    }

    @Test
    void resolvesGlobalMiniPlaceholdersTagsInThePerViewerText() {
        // The global MiniPlaceholders resolver (a <tag> source) is applied during deserialize, alongside the
        // per-viewer %token% bridge, so a per-viewer hologram renders both for the viewer.
        Supplier<TagResolver> greeting = () -> Placeholder.parsed("greeting", "Howdy");
        HologramViewerText text = viewerText(
                perViewer(Map.of(ALICE, source -> source.replace("%name%", "Alice"))),
                greeting,
                new HologramPageState());
        Hologram hologram = text("welcome", "<greeting> %name%");

        assertThat(plain(text.textFor(player(ALICE), hologram, 0))).isEqualTo("Howdy Alice");
    }

    @Test
    void multipleLinesAreJoinedWithNewlinesAsTheHologramDrawsThem() {
        HologramViewerText text = viewerText(perViewer(Map.of(ALICE, source -> source.replace("%name%", "Alice"))));
        Hologram hologram = new Hologram(
                name("two"),
                at(),
                com.uxplima.uxmessentials.holograms.domain.HologramType.TEXT,
                List.of(new HologramLine("top %name%"), new HologramLine("bottom")),
                null,
                null,
                null,
                null,
                com.uxplima.uxmessentials.holograms.domain.Appearance.defaults(),
                com.uxplima.uxmessentials.holograms.domain.Visibility.everyone(),
                com.uxplima.uxmessentials.holograms.domain.Rotation.NONE,
                Hologram.STATIC,
                Instant.EPOCH);

        assertThat(plain(text.textFor(player(ALICE), hologram, 0))).isEqualTo("top Alice\nbottom");
    }

    @Test
    void aMultiPageHologramReadsEachViewersCurrentPage() {
        HologramPageState pages = new HologramPageState();
        HologramViewerText text = viewerText(perViewer(Map.of()), TagResolver::empty, pages);
        Hologram paged = text("guide", "page one")
                .withPageAppended(com.uxplima.uxmessentials.holograms.domain.HologramPage.of(
                        List.of(new HologramLine("page two"))));

        // Alice is advanced to the next page, Bob is left on the default page 0, so each reads their own page.
        pages.advance("guide", ALICE, paged.pageCount());

        assertThat(plain(text.textFor(player(ALICE), paged, 0))).isEqualTo("page two");
        assertThat(plain(text.textFor(player(BOB), paged, 0))).isEqualTo("page one");
    }

    @Test
    void resolvesTheBuiltInPlayerAndPageTokensPerViewer() {
        HologramViewerText text = viewerText(perViewer(Map.of()));
        Hologram paged = text("guide", "{player} - {page}/{pages}")
                .withPageAppended(com.uxplima.uxmessentials.holograms.domain.HologramPage.of(
                        List.of(new HologramLine("second"))));

        // Alice stays on page 0 (the token line); it renders her name and her 1-based page over the 2-page total
        // with no PlaceholderAPI involved.
        assertThat(plain(text.textFor(player(ALICE, "Steve"), paged, 0))).isEqualTo("Steve - 1/2");
    }

    @Test
    void detectsTheBuiltInTokensAsPerViewerText() {
        HologramViewerText text = viewerText(perViewer(Map.of()));

        assertThat(text.hasPerViewerText(text("welcome", "hi {player}"))).isTrue();
        assertThat(text.hasPerViewerText(text("plain", "no tokens"))).isFalse();
    }

    /**
     * A line naming a catalogue entry reads in each viewer's language, so the hologram reads per viewer even with
     * no placeholder on it, and the entry's own {@code {player}} and {@code %papi%} are read for that viewer.
     */
    @Test
    void aCatalogLineReadsInEachViewersLanguage() {
        com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText.useCatalogLines(
                new com.uxplima.uxmessentials.shared.application.message.CatalogLines(
                        (viewer, key, arguments) -> (viewer.uuid().equals(ALICE) ? "Katil {player} " : "Join {player} ")
                                + "%count_" + arguments.get("server") + "%",
                        java.util.Set.of("custom.join")::contains));
        try {
            HologramViewerText text = viewerText(perViewer(Map.of(
                    ALICE, s -> s.replace("%count_skyblock%", "4"),
                    BOB, s -> s.replace("%count_skyblock%", "4"))));
            Hologram join = text("join", "@custom.join server=skyblock");

            assertThat(text.hasPerViewerText(join)).isTrue();
            assertThat(plain(text.textFor(player(ALICE, "Ayse"), join, 0))).isEqualTo("Katil Ayse 4");
            assertThat(plain(text.textFor(player(BOB, "Bob"), join, 0))).isEqualTo("Join Bob 4");
            assertThat(text.hasPerViewerText(text("own", "@everyone"))).isFalse();
        } finally {
            com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText.useCatalogLines(
                    com.uxplima.uxmessentials.shared.application.message.CatalogLines.none());
        }
    }

    /**
     * An animated line moves for a viewer who reads their own values too. The shared entity animated and the
     * per-viewer copy stripped the directive, so a hologram with both showed a still line to everybody.
     */
    @Test
    void anAnimatedLineMovesInEachViewersText() {
        HologramViewerText text = viewerText(perViewer(Map.of(ALICE, source -> source.replace("%name%", "Alice"))));
        Hologram hologram = text("ticker", "<anim:typewriter>hi %name%");

        String first = plain(text.textFor(player(ALICE), hologram, 1));
        String later = plain(text.textFor(player(ALICE), hologram, 4));

        assertThat(first).isNotEqualTo(later);
        assertThat("hi Alice").startsWith(first);
    }

    @Test
    void aSharedHologramReadsTheSameForEverybodyThroughTheGlobalBridge() {
        HologramViewerText text = viewerText(perViewer(Map.of()));
        Hologram hologram = text("online", "online: %server_online%");

        assertThat(plain(text.sharedText(hologram, source -> source.replace("%server_online%", "7"), 0)))
                .isEqualTo("online: 7");
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    private static HologramViewerText viewerText(Function<UUID, UnaryOperator<String>> bridges) {
        return viewerText(bridges, TagResolver::empty, new HologramPageState());
    }

    private static HologramViewerText viewerText(
            Function<UUID, UnaryOperator<String>> bridges, Supplier<TagResolver> globalTags, HologramPageState pages) {
        return new HologramViewerText(bridges, MiniMessage.miniMessage(), globalTags, pages);
    }

    /** A bridge factory returning the per-viewer transform from {@code byViewer}, identity for an unknown uuid. */
    private static Function<UUID, UnaryOperator<String>> perViewer(Map<UUID, UnaryOperator<String>> byViewer) {
        return uuid -> byViewer.getOrDefault(uuid, UnaryOperator.identity());
    }

    private static Hologram text(String name, String line) {
        return Hologram.create(name(name), at(), List.of(new HologramLine(line)), Instant.EPOCH);
    }

    private static HologramName name(String value) {
        return new HologramName(value);
    }

    private static Position at() {
        return new Position(new WorldRef(UUID.randomUUID(), "world"), 0.0, 64.0, 0.0, 0.0f, 0.0f);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Player player(UUID uuid) {
        return player(uuid, "Viewer");
    }

    private static Player player(UUID uuid, String name) {
        return (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) {
                        return uuid;
                    }
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    return null;
                });
    }
}
