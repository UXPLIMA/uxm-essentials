package com.uxplima.uxmessentials.skin.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.UUID;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.adapter.outbound.PaperSkinView;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Putting a skin on a player who is already in the world.
 *
 * <p>The interesting cases are the quiet ones: a player who logged out between the lookup and the apply must not
 * throw, and an unsigned texture has to go on as unsigned rather than being dropped.
 */
class PaperSkinViewTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyingReplacesTheTexturesPropertyOnThePlayersProfile() {
        PlayerMock player = server.addPlayer("Steve");
        PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());

        view().apply(who, new SkinTexture("dmFsdWU=", "c2ln"), SkinModel.CLASSIC);

        assertThat(textures(player)).isNotNull();
        assertThat(textures(player).getValue()).isEqualTo("dmFsdWU=");
        assertThat(textures(player).getSignature()).isEqualTo("c2ln");
    }

    @Test
    void aSecondApplyLeavesOneTexturePropertyRatherThanTwo() {
        PlayerMock player = server.addPlayer("Steve");
        PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());

        view().apply(who, new SkinTexture("Zmlyc3Q=", "c2ln"), SkinModel.CLASSIC);
        view().apply(who, new SkinTexture("c2Vjb25k", "c2ln"), SkinModel.SLIM);

        assertThat(player.getPlayerProfile().getProperties().stream()
                        .filter(property -> property.getName().equals(PaperSkinView.TEXTURES))
                        .count())
                .isEqualTo(1);
        assertThat(textures(player).getValue()).isEqualTo("c2Vjb25k");
    }

    @Test
    void anUnsignedTextureGoesOnUnsigned() {
        PlayerMock player = server.addPlayer("Steve");
        PlayerRef who = new PlayerRef(player.getUniqueId(), player.getName());

        view().apply(who, new SkinTexture("dmFsdWU=", null), SkinModel.CLASSIC);

        assertThat(textures(player).getSignature()).isNull();
    }

    @Test
    void applyingToSomebodyWhoHasLoggedOutDoesNothing() {
        PlayerRef gone = new PlayerRef(UUID.randomUUID(), "Ghost");

        assertThatCode(() -> view().apply(gone, new SkinTexture("dmFsdWU=", "c2ln"), SkinModel.CLASSIC))
                .doesNotThrowAnyException();
    }

    @Test
    void aLoginProfileIsDressedTheSameWay() {
        PlayerMock player = server.addPlayer("Steve");
        com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();

        PaperSkinView.of(profile).dress(new SkinTexture("bG9naW4=", "c2ln"));

        assertThat(profile.getProperties().stream()
                        .filter(property -> property.getName().equals(PaperSkinView.TEXTURES))
                        .findFirst()
                        .orElseThrow()
                        .getValue())
                .isEqualTo("bG9naW4=");
    }

    private PaperSkinView view() {
        return new PaperSkinView(server, new InlineScheduler());
    }

    private static ProfileProperty textures(PlayerMock player) {
        return player.getPlayerProfile().getProperties().stream()
                .filter(property -> property.getName().equals(PaperSkinView.TEXTURES))
                .findFirst()
                .orElseThrow();
    }

    /** A scheduler that runs everything inline, so the test reads the result straight after the call. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position where, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef who, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
