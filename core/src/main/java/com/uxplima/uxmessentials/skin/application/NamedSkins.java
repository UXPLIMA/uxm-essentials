package com.uxplima.uxmessentials.skin.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * The skin a name stands for when somebody asks to wear it: the skin the account of that name here wears, then the
 * paid account of that name at Mojang.
 *
 * <p>The account here comes first because Mojang knows only paid Java names. A Bedrock player ({@code .Steve}), an
 * offline-mode account, or one who chose a skin of their own would otherwise come back as nobody's skin or as a
 * stranger's who happens to own the name. Both lookups block, so a caller reaches this off a tick thread.
 */
@NullMarked
public final class NamedSkins {

    private final PlayerLookup players;
    private final DressLogin dressLogin;
    private final SkinTextures textures;

    public NamedSkins(PlayerLookup players, DressLogin dressLogin, SkinTextures textures) {
        this.players = Objects.requireNonNull(players, "players");
        this.dressLogin = Objects.requireNonNull(dressLogin, "dressLogin");
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    /** The texture {@code name} stands for, or empty when neither an account here nor Mojang has one. */
    public Optional<SkinTexture> byName(String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> known = players.findByName(name);
        if (known.isPresent()) {
            Optional<SkinTexture> worn =
                    dressLogin.worn(known.get().uuid(), known.get().name()).map(DressLogin.Dressed::texture);
            if (worn.isPresent()) {
                return worn;
            }
        }
        return textures.fetchNow(name);
    }
}
