package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.skin.domain.event.SkinCleared;
import org.junit.jupiter.api.Test;

/**
 * Clearing a skin, which is not the same as removing one: the player drops their own choice and goes back to
 * whatever the join order gives them, which on a cracked server is usually the pool entry they started with.
 */
class ClearSkinTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final SkinTexture CHOSEN = new SkinTexture("Y2hvc2Vu", "c2ln");
    private static final SkinTexture POOL = new SkinTexture("cG9vbA==", "c2ln");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);

    private final SkinFakes.Repository repository = new SkinFakes.Repository();
    private final SkinFakes.View view = new SkinFakes.View();
    private final SkinFakes.Events events = new SkinFakes.Events();

    @Test
    void clearingDropsTheStoredChoiceAndRedressesFromTheJoinOrder() {
        store(new SkinSource.ByName("Notch"), CHOSEN);
        SkinConfig config = SkinConfig.from(new SkinFakes.FixedConfig(Map.of("login.premium-skin", false)))
                .withDefaultPool(List.of("Alex"));

        assertThat(clearSkin(config, new SkinFakes.Textures(Map.of("Alex", POOL)))
                        .clear(WHO))
                .isEqualTo(ClearSkin.Outcome.CLEARED);
        assertThat(view.applied).containsExactly(POOL);
        assertThat(events.published).singleElement().isInstanceOf(SkinCleared.class);
    }

    @Test
    void aPlayerWhoChoseNothingIsToldSoAndNothingIsPublished() {
        assertThat(clearSkin(SkinConfig.defaults(), new SkinFakes.Textures(Map.of()))
                        .clear(WHO))
                .isEqualTo(ClearSkin.Outcome.NOTHING_TO_CLEAR);
        assertThat(events.published).isEmpty();
        assertThat(view.applied).isEmpty();
    }

    @Test
    void aClearWithNothingToFallBackOnStillDropsTheChoice() {
        // No premium account, no Bedrock, no pool: the row goes and the player keeps whatever the client draws.
        store(new SkinSource.ByName("Notch"), CHOSEN);
        SkinConfig config = SkinConfig.from(new SkinFakes.FixedConfig(Map.of("login.premium-skin", false)));

        assertThat(clearSkin(config, new SkinFakes.Textures(Map.of())).clear(WHO))
                .isEqualTo(ClearSkin.Outcome.CLEARED);
        assertThat(repository.isEmpty()).isTrue();
        assertThat(view.applied).isEmpty();
    }

    private void store(SkinSource source, SkinTexture texture) {
        repository.save(new PlayerSkin(WHO, source, texture, SkinModel.CLASSIC, Instant.EPOCH));
    }

    private ClearSkin clearSkin(SkinConfig config, SkinFakes.Textures textures) {
        DressLogin dressLogin =
                new DressLogin(repository, textures, SkinFakes.noBedrock(), config, new SkinFakes.NoopLogger());
        return new ClearSkin(repository, dressLogin, view, events, CLOCK);
    }
}
