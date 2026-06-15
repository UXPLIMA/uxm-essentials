package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The bundle of read seams the {@link PlaceholderResolver} consults, one per feature context that
 * contributes placeholders. Every seam is optional: a disabled (or not yet landed) context contributes no
 * seam, and the resolver degrades that context's placeholders to their empty/"-" default rather than
 * failing.
 *
 * <p>The bundle is assembled once in bootstrap through {@link Builder} as each context's adapters are
 * wired, then handed to the resolver. It holds only adapter-side read seams — no PlaceholderAPI type and
 * no live {@code Player} — so it is a plain value that the resolver test can populate with fakes.
 */
public final class PlaceholderContexts {

    private final @Nullable HomesPlaceholders homes;
    private final @Nullable EconomyPlaceholders economy;
    private final @Nullable PresencePlaceholders presence;
    private final @Nullable PlayerstatePlaceholders playerstate;
    private final @Nullable KitsPlaceholders kits;
    private final @Nullable VaultsPlaceholders vaults;
    private final @Nullable ModerationPlaceholders moderation;
    private final @Nullable TeleportPlaceholders teleport;
    private final @Nullable VotePlaceholders vote;

    private PlaceholderContexts(Builder builder) {
        this.homes = builder.homes;
        this.economy = builder.economy;
        this.presence = builder.presence;
        this.playerstate = builder.playerstate;
        this.kits = builder.kits;
        this.vaults = builder.vaults;
        this.moderation = builder.moderation;
        this.teleport = builder.teleport;
        this.vote = builder.vote;
    }

    /** A fresh, empty builder — every seam starts absent until a wired context registers it. */
    public static Builder builder() {
        return new Builder();
    }

    public Optional<HomesPlaceholders> homes() {
        return Optional.ofNullable(homes);
    }

    public Optional<EconomyPlaceholders> economy() {
        return Optional.ofNullable(economy);
    }

    public Optional<PresencePlaceholders> presence() {
        return Optional.ofNullable(presence);
    }

    public Optional<PlayerstatePlaceholders> playerstate() {
        return Optional.ofNullable(playerstate);
    }

    public Optional<KitsPlaceholders> kits() {
        return Optional.ofNullable(kits);
    }

    public Optional<VaultsPlaceholders> vaults() {
        return Optional.ofNullable(vaults);
    }

    public Optional<ModerationPlaceholders> moderation() {
        return Optional.ofNullable(moderation);
    }

    public Optional<TeleportPlaceholders> teleport() {
        return Optional.ofNullable(teleport);
    }

    public Optional<VotePlaceholders> vote() {
        return Optional.ofNullable(vote);
    }

    /** True when no context registered a seam — registering the expansion would surface nothing. */
    public boolean isEmpty() {
        return homes == null
                && economy == null
                && presence == null
                && playerstate == null
                && kits == null
                && vaults == null
                && moderation == null
                && teleport == null
                && vote == null;
    }

    /** Mutable collector for the seams, filled as each context's adapters are wired in bootstrap. */
    public static final class Builder {

        private @Nullable HomesPlaceholders homes;
        private @Nullable EconomyPlaceholders economy;
        private @Nullable PresencePlaceholders presence;
        private @Nullable PlayerstatePlaceholders playerstate;
        private @Nullable KitsPlaceholders kits;
        private @Nullable VaultsPlaceholders vaults;
        private @Nullable ModerationPlaceholders moderation;
        private @Nullable TeleportPlaceholders teleport;
        private @Nullable VotePlaceholders vote;

        private Builder() {}

        public Builder homes(HomesPlaceholders seam) {
            this.homes = seam;
            return this;
        }

        public Builder economy(EconomyPlaceholders seam) {
            this.economy = seam;
            return this;
        }

        public Builder presence(PresencePlaceholders seam) {
            this.presence = seam;
            return this;
        }

        public Builder playerstate(PlayerstatePlaceholders seam) {
            this.playerstate = seam;
            return this;
        }

        public Builder kits(KitsPlaceholders seam) {
            this.kits = seam;
            return this;
        }

        public Builder vaults(VaultsPlaceholders seam) {
            this.vaults = seam;
            return this;
        }

        public Builder moderation(ModerationPlaceholders seam) {
            this.moderation = seam;
            return this;
        }

        public Builder teleport(TeleportPlaceholders seam) {
            this.teleport = seam;
            return this;
        }

        public Builder vote(VotePlaceholders seam) {
            this.vote = seam;
            return this;
        }

        public PlaceholderContexts build() {
            return new PlaceholderContexts(this);
        }
    }
}
