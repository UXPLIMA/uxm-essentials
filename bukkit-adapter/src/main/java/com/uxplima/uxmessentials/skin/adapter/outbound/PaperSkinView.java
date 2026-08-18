package com.uxplima.uxmessentials.skin.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.LoginProfile;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.NullMarked;

/**
 * Puts a skin on a player who is already in the world, by replacing the {@code textures} property on their own
 * profile and handing it back to the server.
 *
 * <p>The server then rebuilds the profile and re-sends the player to everyone who can see them. Nothing here
 * sends a packet, which is what keeps the whole operation free of the usual skin-plugin hazards: no inventory to
 * restore, no position to re-sync, no gamemode or potion effects to reapply, and nothing to get wrong on a
 * Folia region hop. The work runs on the player's own thread through the {@link Scheduler} port, and a player who
 * has logged out in the meantime is simply left alone.
 */
@NullMarked
public final class PaperSkinView implements SkinView {

    /** The profile property a skin travels in. */
    public static final String TEXTURES = "textures";

    private final Server server;
    private final Scheduler scheduler;

    public PaperSkinView(Server server, Scheduler scheduler) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void apply(PlayerRef who, SkinTexture texture, SkinModel model) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(model, "model");
        scheduler.onEntity(who, () -> {
            Player player = server.getPlayer(who.uuid());
            if (player == null) {
                return;
            }
            PlayerProfile profile = player.getPlayerProfile();
            dress(profile, texture);
            player.setPlayerProfile(profile);
        });
    }

    /** The joining player's profile as a {@link LoginProfile}, so the login path dresses it the same way. */
    public static LoginProfile of(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return texture -> dress(profile, texture);
    }

    /** Replace the texture property on {@code profile}, leaving every other property alone. */
    private static void dress(PlayerProfile profile, SkinTexture texture) {
        profile.removeProperty(TEXTURES);
        profile.setProperty(new ProfileProperty(TEXTURES, texture.value(), texture.signature()));
    }
}
