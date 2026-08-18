package com.uxplima.uxmessentials.skin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.junit.jupiter.api.Test;

/**
 * The skin aggregate and the sources it can be resolved from.
 *
 * <p>A stored skin keeps both halves: the texture the client is dressed with, and the source it came from. The
 * texture alone would make a join a pure database read but leave {@code /skin update} with nothing to re-resolve;
 * the source alone would put a network call in front of every login. Keeping both is what lets the row be the
 * whole answer at login time and still be refreshable on demand.
 */
class PlayerSkinTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");

    private static final SkinTexture TEXTURE = new SkinTexture("dmFsdWU=", "c2ln");

    @Test
    void aStoredSkinKeepsTheSourceItWasResolvedFrom() {
        PlayerSkin skin =
                new PlayerSkin(WHO, new SkinSource.ByName("Notch"), TEXTURE, SkinModel.CLASSIC, Instant.EPOCH);

        assertThat(skin.source()).isEqualTo(new SkinSource.ByName("Notch"));
        assertThat(skin.model()).isEqualTo(SkinModel.CLASSIC);
        assertThat(skin.texture()).isEqualTo(TEXTURE);
    }

    @Test
    void aSourceRejectsABlankValue() {
        assertThatThrownBy(() -> new SkinSource.ByName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SkinSource.ByUrl("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everySourceNamesItselfForStorage() {
        // A row keeps a source as a type plus one value, so each source has to expose that value.
        assertThat(new SkinSource.ByName("Notch").value()).isEqualTo("Notch");
        assertThat(new SkinSource.ByUrl("https://example.invalid/s.png").value())
                .isEqualTo("https://example.invalid/s.png");
        assertThat(new SkinSource.ByFile("pirate").value()).isEqualTo("pirate");
        assertThat(new SkinSource.Bedrock("2535000000000000").value()).isEqualTo("2535000000000000");
        assertThat(new SkinSource.Fallback("Alex").value()).isEqualTo("Alex");
    }

    @Test
    void onlyTheSlimModelIsSlim() {
        assertThat(SkinModel.SLIM.slim()).isTrue();
        assertThat(SkinModel.CLASSIC.slim()).isFalse();
        assertThat(SkinModel.of(true)).isEqualTo(SkinModel.SLIM);
        assertThat(SkinModel.of(false)).isEqualTo(SkinModel.CLASSIC);
    }

    @Test
    void aSkinOwnedByAPlayerIsTheSameSkinWhicheverNameTheyLastLoggedInUnder() {
        // PlayerRef equality is by uuid, so a rename does not turn a stored skin into a different one.
        UUID uuid = UUID.randomUUID();
        PlayerSkin first = new PlayerSkin(
                new PlayerRef(uuid, "Steve"), new SkinSource.ByName("Notch"), TEXTURE, SkinModel.SLIM, Instant.EPOCH);
        PlayerSkin renamed = new PlayerSkin(
                new PlayerRef(uuid, "Steve2"), new SkinSource.ByName("Notch"), TEXTURE, SkinModel.SLIM, Instant.EPOCH);

        assertThat(renamed).isEqualTo(first);
    }
}
