package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmlib.board.BoardSettings;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A board head on a HUD line shows an offline-mode player in the skin they wear here, which the client could not find
 * at Mojang under their name.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoardHeadScenarioTest {

    private static final String OWN_HEAD = "<gray>You:</gray> <uxm_essentials_head>";
    private static final String SKIN = "ZXlKMFpYaDBkWEpsY3lJNmUzMTk=";
    private static final String SIGNATURE = "c2lnbmF0dXJl";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void anOfflineModePlayerSeesTheirOwnHeadInTheSkinTheyWear() {
        ScenarioPlayer dressed = running.join("Dressed", player -> wear(player, SKIN, SIGNATURE));

        PlayerHeadObjectContents head = headOn(HudText.render(dressed.getUniqueId(), OWN_HEAD));

        assertThat(head.profileProperties()).singleElement().satisfies(textures -> {
            assertThat(textures.name()).isEqualTo("textures");
            assertThat(textures.value()).isEqualTo(SKIN);
            assertThat(textures.signature()).isEqualTo(SIGNATURE);
        });
        assertThat(head.name()).as("the client asks Mojang about nobody").isNull();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aPlayerWearingNoSkinIsTheEmptyHeadAndSoIsOneWhoLeft() {
        ScenarioPlayer bare = running.join("Bare");
        ScenarioPlayer gone = running.join("Gone", player -> wear(player, SKIN, null));
        UUID goneId = gone.getUniqueId();
        running.quit(gone);

        assertThat(headOn(HudText.render(bare.getUniqueId(), OWN_HEAD)).texture())
                .isEqualTo(Key.key(BoardSettings.STEVE));
        assertThat(headOn(HudText.parse(goneId, OWN_HEAD)).texture())
                .as("a player no board shows is let go as they leave")
                .isEqualTo(Key.key(BoardSettings.STEVE));
        assertThat(running.problems()).isEmpty();
    }

    private static void wear(ScenarioPlayer player, String skin, @Nullable String signature) {
        PlayerProfile profile = player.getPlayerProfile();
        profile.setProperty(new ProfileProperty("textures", skin, signature));
        player.setPlayerProfile(profile);
    }

    private static PlayerHeadObjectContents headOn(Component line) {
        @Nullable PlayerHeadObjectContents head = find(line);
        if (head == null) {
            throw new AssertionError("no head on " + line);
        }
        return head;
    }

    private static @Nullable PlayerHeadObjectContents find(Component line) {
        if (line instanceof ObjectComponent object && object.contents() instanceof PlayerHeadObjectContents head) {
            return head;
        }
        for (Component child : line.children()) {
            @Nullable PlayerHeadObjectContents head = find(child);
            if (head != null) {
                return head;
            }
        }
        return null;
    }
}
