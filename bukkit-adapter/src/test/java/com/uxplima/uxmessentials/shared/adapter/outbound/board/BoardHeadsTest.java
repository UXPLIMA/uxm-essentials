package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmlib.board.Board;
import com.uxplima.uxmlib.board.BoardAnswers;
import com.uxplima.uxmlib.board.BoardPeriod;
import com.uxplima.uxmlib.board.BoardSettings;
import com.uxplima.uxmlib.board.BoardStore;
import com.uxplima.uxmlib.board.Leaderboard;
import com.uxplima.uxmlib.board.Ranked;
import com.uxplima.uxmlib.board.Standings;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardHeadsTest {

    /** An offline-mode account: a version 3 id. */
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8));
    /** A Mojang account: a version 4 id. */
    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    /** A Bedrock account through Floodgate: a version 0 id. */
    private static final UUID BEDROCK = new UUID(0L, 2_535_400_000_000_001L);

    private static final SkinTexture STORED = new SkinTexture("c3RvcmVk", "c2lnbmVk");
    private static final SkinTexture WORN = new SkinTexture("d29ybg==", null);

    private final Map<UUID, Long> prestige = new HashMap<>();
    private final Map<UUID, SkinTexture> store = new HashMap<>();
    private final List<UUID> asked = new ArrayList<>();
    private final BoardSkins skins = new BoardSkins();
    private final BoardHeads heads = new BoardHeads();
    private Leaderboard leaderboard;

    @BeforeEach
    void standTheBoard() {
        prestige.put(OFFLINE, 30L);
        prestige.put(MOJANG, 20L);
        prestige.put(BEDROCK, 10L);
        BoardStore nothing = new NothingStored();
        leaderboard = new Leaderboard(
                nothing,
                List.of(Board.snapshot("prestige", new HeldSnapshot(() -> Map.copyOf(prestige)))),
                player -> player.equals(MOJANG) ? "Notch" : "BE_Steve",
                BoardSettings::defaults,
                Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC));
        EssentialsBoardText words = new EssentialsBoardText((reader, key, values) -> key.key(), Map.of());
        heads.answerFrom(new BoardAnswers(
                leaderboard,
                new Standings(nothing, leaderboard),
                words,
                () -> BoardSettings.STEVE,
                player -> Optional.empty(),
                skins::of));
        leaderboard.refresh();
    }

    @Test
    void aHolderElsewhereIsDrawnFromTheSkinTheStoreHolds() {
        store.put(OFFLINE, STORED);

        warm();

        assertThat(skinOf(drawn("<uxm_essentials_top_prestige_alltime_1_head>")))
                .isEqualTo(STORED);
    }

    @Test
    void onlyAccountsMojangDidNotMakeAreAskedForAndAMojangOneKeepsItsName() {
        store.put(MOJANG, STORED);

        warm();

        assertThat(asked).containsExactlyInAnyOrder(OFFLINE, BEDROCK);
        assertThat(head(drawn("<uxm_essentials_top_prestige_alltime_2_head>")).name())
                .isEqualTo("Notch");
    }

    @Test
    void aHolderWithNoSkinAnywhereIsTheEmptyHead() {
        warm();

        assertThat(head(drawn("<uxm_essentials_top_prestige_alltime_3_head>")).texture())
                .isEqualTo(Key.key(BoardSettings.STEVE));
    }

    @Test
    void aPlayerHereIsDrawnInWhatTheyWearAndTheStoreIsNotAsked() {
        store.put(OFFLINE, STORED);
        skins.arrived(OFFLINE, Optional.of(WORN));

        warm();

        assertThat(asked).doesNotContain(OFFLINE);
        assertThat(skinOf(drawn("<uxm_essentials_top_prestige_alltime_1_head>")))
                .isEqualTo(WORN);
        assertThat(skinOf(MiniMessage.miniMessage().deserialize("<uxm_essentials_head>", heads.tags(OFFLINE, "Alex"))))
                .isEqualTo(WORN);
    }

    @Test
    void aSkinChangedHereIsDrawnFromThen() {
        skins.arrived(BEDROCK, Optional.empty());
        warm();

        skins.wears(BEDROCK, WORN);

        assertThat(skinOf(drawn("<uxm_essentials_top_prestige_alltime_3_head>")))
                .isEqualTo(WORN);
    }

    @Test
    void aSkinIsLetGoOnceItsOwnerIsNeitherHereNorShown() {
        skins.arrived(OFFLINE, Optional.of(WORN));
        warm();
        skins.left(OFFLINE);
        assertThat(skins.of(OFFLINE)).as("still on the board").contains(stored(WORN));

        prestige.remove(OFFLINE);
        leaderboard.refresh();
        warm();

        assertThat(skins.of(OFFLINE)).isEmpty();
    }

    @Test
    void whatTheBoardsDoNotAnswerIsLeftAsWritten() {
        warm();

        assertThat(plain(drawn("<uxm_essentials_top_kills_alltime_1_head>")))
                .isEqualTo("<uxm_essentials_top_kills_alltime_1_head>");
        assertThat(plain(drawn("<uxm_essentials_head>"))).as("no reader").isEqualTo("<uxm_essentials_head>");
        assertThat(plain(MiniMessage.miniMessage()
                        .deserialize("<uxm_essentials_head>", new BoardHeads().tags(OFFLINE, "Alex"))))
                .as("before the boards are built")
                .isEqualTo("<uxm_essentials_head>");
    }

    @Test
    void aLineIsKnownToHoldAHeadInAnyCase() {
        assertThat(BoardHeads.mentions("Top: <UXM_Essentials_top_prestige_alltime_1_head>"))
                .isTrue();
        assertThat(BoardHeads.mentions("<head:Notch> Notch")).isFalse();
        assertThat(BoardHeads.mentionsReader("You: <uxm_essentials_head>")).isTrue();
        assertThat(BoardHeads.mentionsReader("<uxm_essentials_top_prestige_alltime_1_head>"))
                .isFalse();
    }

    private void warm() {
        skins.warm(BoardSkins.shownOn(leaderboard, BoardSettings.defaults().size()), this::fromStore);
    }

    /** The skin store, noting who it was asked about. */
    private Optional<SkinTexture> fromStore(UUID player) {
        asked.add(player);
        return Optional.ofNullable(store.get(player));
    }

    private Component drawn(String line) {
        return MiniMessage.miniMessage().deserialize(line, heads.tags(null, null));
    }

    private static com.uxplima.uxmlib.board.StoredSkin stored(SkinTexture texture) {
        return new com.uxplima.uxmlib.board.StoredSkin(texture.value(), texture.signature());
    }

    private static PlayerHeadObjectContents head(Component line) {
        Component object =
                line instanceof ObjectComponent ? line : line.children().getFirst();
        assertThat(object).isInstanceOf(ObjectComponent.class);
        return (PlayerHeadObjectContents) ((ObjectComponent) object).contents();
    }

    private static SkinTexture skinOf(Component line) {
        PlayerHeadObjectContents.ProfileProperty textures =
                head(line).profileProperties().getFirst();
        assertThat(textures.name()).isEqualTo("textures");
        return new SkinTexture(textures.value(), textures.signature());
    }

    private static String plain(@Nullable Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line);
    }

    /** A store with no counted rows: the only board here is a snapshot. */
    private static final class NothingStored implements BoardStore {

        @Override
        public void add(UUID player, Board board, Map<BoardPeriod, Long> windows, long amount, long at) {}

        @Override
        public List<Ranked> top(Board board, BoardPeriod period, long window, int limit) {
            return List.of();
        }

        @Override
        public Map<Long, Integer> countByScore(Board board, BoardPeriod period, long window) {
            return Map.of();
        }

        @Override
        public Map<Slot, Long> scoresOf(UUID player, Map<BoardPeriod, Long> windows) {
            return Map.of();
        }

        @Override
        public void forgetBefore(BoardPeriod period, long window) {}

        @Override
        public void erase(UUID player) {}

        @Override
        public void name(UUID player, String name) {}
    }
}
