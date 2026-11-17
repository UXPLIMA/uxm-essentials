package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.junit.jupiter.api.Test;

/** A dropped skin comes off a player online here at once; one who is not here gets the change when they join. */
class DropSkinTest {

    private static final PlayerRef ADA = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"), "Ada");
    private static final SkinTexture CHOSEN = new SkinTexture("chosen", "sig-chosen");
    private static final SkinTexture PREMIUM = new SkinTexture("premium", "sig-premium");

    private final Rows rows = new Rows();
    private final Mojang mojang = new Mojang();
    private final RecordingView view = new RecordingView();
    private final DressLogin dressLogin =
            new DressLogin(rows, mojang, BedrockSkins.none(), SkinConfig.defaults(), new QuietLog());

    @Test
    void aDropOfAPlayerOnlineHereDressesThemInTheJoinOrderSkin() {
        rows.save(new PlayerSkin(ADA, new SkinSource.ByName("Steve"), CHOSEN, SkinModel.CLASSIC, Instant.EPOCH));
        DropSkin drop = new DropSkin(rows, dressLogin, view, new OnlineLookup().with(ADA));

        assertThat(drop.drop(ADA)).isEqualTo(DropSkin.Outcome.DROPPED);
        assertThat(rows.find(ADA.uuid())).isEmpty();
        assertThat(view.dressed).containsExactly("Ada:premium");
    }

    @Test
    void aDropOfAPlayerWhoIsNotHereOnlyRemovesTheRow() {
        rows.save(new PlayerSkin(ADA, new SkinSource.ByName("Steve"), CHOSEN, SkinModel.CLASSIC, Instant.EPOCH));
        DropSkin drop = new DropSkin(rows, dressLogin, view, new OnlineLookup());

        assertThat(drop.drop(ADA)).isEqualTo(DropSkin.Outcome.DROPPED);
        assertThat(rows.find(ADA.uuid())).isEmpty();
        assertThat(view.dressed).isEmpty();
        assertThat(mojang.asked).isEmpty();
    }

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

    /** Mojang: Ada is a paid account. */
    private static final class Mojang implements SkinTextures {
        private final List<String> asked = new ArrayList<>();

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return "Ada".equals(username) ? Optional.of(PREMIUM) : Optional.empty();
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
