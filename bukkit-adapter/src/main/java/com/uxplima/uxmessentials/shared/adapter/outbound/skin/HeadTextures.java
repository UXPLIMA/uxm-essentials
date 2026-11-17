package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * The texture a player head is drawn with, so a head shows the skin its owner wears here rather than whatever Mojang
 * or the client makes of their name or uuid.
 *
 * <p>Three sources, in order: the live profile of an owner online here ({@link #worn(PlayerProfile)}, read on their
 * own thread), the skin stored for the account by the skin module, and Mojang. Mojang is asked only about a Mojang
 * account, one with a version 4 uuid: an offline-mode account (version 3) or a Bedrock one through Floodgate
 * (version 0) shares its name, at best, with a stranger. A name nobody here has used is sent to Mojang only on a
 * server whose logins Mojang authenticates.
 *
 * <p>{@link #of(PlayerRef)} and {@link #unknown(String)} read the database and the network, so they run on the async
 * lane.
 */
@NullMarked
public final class HeadTextures {

    private static final String TEXTURES = "textures";
    private static final int MOJANG_UUID_VERSION = 4;

    private final PlayerLookup players;
    private final StoredSkins stored;
    private final SkinTextures mojang;

    public HeadTextures(PlayerLookup players, StoredSkins stored, SkinTextures mojang) {
        this.players = Objects.requireNonNull(players, "players");
        this.stored = Objects.requireNonNull(stored, "stored");
        this.mojang = Objects.requireNonNull(mojang, "mojang");
    }

    /** The texture for a known account that is not online here, or empty to draw the plain head. */
    public Optional<SkinTexture> of(PlayerRef account) {
        Objects.requireNonNull(account, "account");
        Optional<SkinTexture> chosen = stored.of(account.uuid());
        if (chosen.isPresent()) {
            return chosen;
        }
        return account.uuid().version() == MOJANG_UUID_VERSION ? mojang.fetchNow(account.name()) : Optional.empty();
    }

    /** The texture for a name no account here goes by, or empty to draw the plain head. */
    public Optional<SkinTexture> unknown(String name) {
        Objects.requireNonNull(name, "name");
        return players.nameCase() == NameCase.INSENSITIVE ? mojang.fetchNow(name) : Optional.empty();
    }

    /** The texture an online player wears right now, read off their own profile on their own thread. */
    public static Optional<SkinTexture> worn(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        for (ProfileProperty property : profile.getProperties()) {
            if (TEXTURES.equals(property.getName())) {
                return Optional.of(new SkinTexture(property.getValue(), property.getSignature()));
            }
        }
        return Optional.empty();
    }

    /** The skin the skin module stores for an account, by uuid. */
    @FunctionalInterface
    public interface StoredSkins {

        /** The stored texture of {@code account}, or empty when none is stored or the skin module is off. */
        Optional<SkinTexture> of(UUID account);

        /** Nothing stored: the skin module is off. */
        static StoredSkins none() {
            return account -> Optional.empty();
        }
    }
}
