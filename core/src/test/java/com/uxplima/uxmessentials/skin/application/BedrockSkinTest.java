package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * A Bedrock player wears their own Bedrock skin: at join, on {@code /skin update}, and when somebody takes their skin
 * by name. Their gamertag is never sent to Mojang, where a prefixed name can belong to a stranger.
 */
class BedrockSkinTest {

    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final PlayerRef STEVE = new PlayerRef(new UUID(0L, 0x0009_0000_1234L), "BE_Steve");
    private static final PlayerRef ADA = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"), "Ada");
    private static final SkinTexture BEDROCK = new SkinTexture("bedrock", "sig-bedrock");
    private static final SkinTexture REFRESHED = new SkinTexture("refreshed", "sig-refreshed");
    private static final SkinTexture STRANGER = new SkinTexture("stranger", "sig-stranger");
    private static final SkinTexture POOL = new SkinTexture("pool", "sig-pool");
    private static final SkinTexture JAVA_ADA = new SkinTexture("ada", "sig-ada");

    private final Rows rows = new Rows();
    private final Mojang mojang = new Mojang(Map.of("BE_Steve", STRANGER, "Alex", POOL, "Ada", JAVA_ADA));
    private final Geyser geyser = new Geyser(STEVE.uuid());
    private final RecordingView view = new RecordingView();

    @Test
    void aBedrockJoinerWhosePrefixedNameIsAPaidJavaAccountWearsTheirBedrockSkinAndMojangIsNotAsked() {
        geyser.skin = Optional.of(BEDROCK);

        Optional<DressLogin.Dressed> dressed = dressLogin(SkinConfig.defaults()).resolve(STEVE.uuid(), STEVE.name());

        assertThat(dressed).map(DressLogin.Dressed::texture).contains(BEDROCK);
        assertThat(mojang.asked).isEmpty();
    }

    @Test
    void aBedrockJoinerWhoseSkinCannotBeReadGetsThePoolRatherThanAStrangersSkin() {
        SkinConfig config = SkinConfig.defaults().withDefaultPool(List.of("Alex"));

        Optional<DressLogin.Dressed> dressed = dressLogin(config).resolve(STEVE.uuid(), STEVE.name());

        assertThat(dressed).map(DressLogin.Dressed::texture).contains(POOL);
        assertThat(mojang.asked).containsExactly("Alex");
    }

    @Test
    void aLinkedBedrockJoinerWhoseBedrockSkinCannotBeReadWearsTheirJavaAccountsSkin() {
        geyser.linked = Optional.of(ADA);

        Optional<DressLogin.Dressed> dressed = dressLogin(SkinConfig.defaults().withDefaultPool(List.of("Alex")))
                .resolve(STEVE.uuid(), STEVE.name());

        assertThat(dressed).map(DressLogin.Dressed::texture).contains(JAVA_ADA);
        assertThat(mojang.asked).containsExactly("Ada");
    }

    @Test
    void anUpdateReadsAStoredBedrockSkinAgainFromBedrock() {
        rows.save(bedrockRow());
        geyser.skin = Optional.of(REFRESHED);
        UpdateSkin update = new UpdateSkin(
                rows,
                mojang,
                names(new OnlineLookup().with(STEVE)),
                geyser,
                new NoUploads(),
                view,
                new RecordingEvents(),
                CLOCK);

        assertThat(update.update(STEVE)).isEqualTo(UpdateSkin.Outcome.UPDATED);
        assertThat(view.dressed).containsExactly("BE_Steve:refreshed");
        assertThat(rows.find(STEVE.uuid())).map(PlayerSkin::texture).contains(REFRESHED);
    }

    @Test
    void takingTheSkinOfAnOnlineBedrockPlayerByNameCopiesTheirSkinNotAStrangers() {
        rows.save(bedrockRow());
        SetSkin set = new SetSkin(
                rows,
                mojang,
                names(new OnlineLookup().with(STEVE, ADA)),
                new NoUploads(),
                view,
                new FakePermissions(),
                new ClockCooldowns(CLOCK),
                new RecordingEvents(),
                SkinConfig.defaults(),
                CLOCK);

        SetSkin.Outcome outcome = set.set(ADA, ADA, new SkinSource.ByName("BE_Steve"), SkinModel.CLASSIC);

        assertThat(outcome).isEqualTo(SetSkin.Outcome.APPLIED);
        assertThat(view.dressed).containsExactly("Ada:bedrock");
        assertThat(mojang.asked).isEmpty();
    }

    @Test
    void aBedrockPlayerNamedOnlyOnceTheyAreInIsDressedInTheirBedrockSkinThen() {
        geyser.skin = Optional.of(BEDROCK);

        new DressBedrockArrival(rows, dressLogin(SkinConfig.defaults()), view, SkinConfig.defaults()).arrived(STEVE);

        assertThat(view.dressed).containsExactly("BE_Steve:bedrock");
        assertThat(rows.find(STEVE.uuid())).map(PlayerSkin::texture).contains(BEDROCK);
        assertThat(mojang.asked).isEmpty();
    }

    @Test
    void aBedrockPlayerWhoChoseTheirOwnSkinKeepsItWhenTheyAreNamedLate() {
        geyser.skin = Optional.of(BEDROCK);
        rows.save(new PlayerSkin(STEVE, new SkinSource.ByName("Ada"), JAVA_ADA, SkinModel.CLASSIC, Instant.EPOCH));

        new DressBedrockArrival(rows, dressLogin(SkinConfig.defaults()), view, SkinConfig.defaults()).arrived(STEVE);

        assertThat(view.dressed).isEmpty();
    }

    @Test
    void aLateBedrockPlayerWhoseSkinCannotBeReadKeepsWhatTheyJoinedIn() {
        SkinConfig config = SkinConfig.defaults().withDefaultPool(List.of("Alex"));

        new DressBedrockArrival(rows, dressLogin(config), view, config).arrived(STEVE);

        assertThat(view.dressed).isEmpty();
    }

    private DressLogin dressLogin(SkinConfig config) {
        return new DressLogin(rows, mojang, geyser, config, new QuietLog());
    }

    private NamedSkins names(OnlineLookup players) {
        return new NamedSkins(players, dressLogin(SkinConfig.defaults()), mojang);
    }

    private static PlayerSkin bedrockRow() {
        return new PlayerSkin(
                STEVE, new SkinSource.Bedrock(STEVE.uuid().toString()), BEDROCK, SkinModel.CLASSIC, Instant.EPOCH);
    }

    /** The stored skins. */
    private static final class Rows implements SkinRepository {
        private final Map<UUID, PlayerSkin> rows = new HashMap<>();

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void save(PlayerSkin skin) {
            rows.put(skin.owner().uuid(), skin);
        }

        @Override
        public void delete(UUID player) {
            rows.remove(player);
        }
    }

    /** Mojang: the paid Java accounts, and every name it was asked about. */
    private static final class Mojang implements SkinTextures {
        private final Map<String, SkinTexture> accounts;
        private final List<String> asked = new ArrayList<>();

        Mojang(Map<String, SkinTexture> accounts) {
            this.accounts = accounts;
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return Optional.ofNullable(accounts.get(username));
        }
    }

    /** Floodgate and the Geyser skin service: one Bedrock player, whose skin may not be readable. */
    private static final class Geyser implements BedrockSkins {
        private final UUID bedrockPlayer;
        private Optional<SkinTexture> skin = Optional.empty();
        private Optional<PlayerRef> linked = Optional.empty();

        Geyser(UUID bedrockPlayer) {
            this.bedrockPlayer = bedrockPlayer;
        }

        @Override
        public Optional<SkinTexture> byPlayer(UUID player) {
            return isBedrock(player) ? skin : Optional.empty();
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean isBedrock(UUID player) {
            return bedrockPlayer.equals(player);
        }

        @Override
        public Optional<PlayerRef> linkedJava(UUID player) {
            return isBedrock(player) ? linked : Optional.empty();
        }
    }

    private static final class NoUploads implements SkinUploads {
        @Override
        public Optional<SkinTexture> fromUrl(String url, SkinModel model) {
            return Optional.empty();
        }

        @Override
        public Optional<SkinTexture> fromFile(String fileName, SkinModel model) {
            return Optional.empty();
        }
    }

    private static final class RecordingView implements SkinView {
        private final List<String> dressed = new ArrayList<>();

        @Override
        public void apply(PlayerRef who, SkinTexture texture, SkinModel model) {
            dressed.add(who.name() + ":" + texture.value());
        }
    }
}
