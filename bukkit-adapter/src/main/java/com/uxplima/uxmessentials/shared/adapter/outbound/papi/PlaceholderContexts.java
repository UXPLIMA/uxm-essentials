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
    private final @Nullable WarpsPlaceholders warps;
    private final @Nullable PlayerwarpsPlaceholders playerwarps;
    private final @Nullable ModerationPlaceholders moderation;
    private final @Nullable TeleportPlaceholders teleport;
    private final @Nullable VotePlaceholders vote;
    private final @Nullable MessagingPlaceholders messaging;
    private final @Nullable StaffPlaceholders staff;
    private final @Nullable DiscordlinkPlaceholders discordlink;
    private final @Nullable HologramsPlaceholders holograms;
    private final @Nullable CommunicationPlaceholders communication;

    private PlaceholderContexts(Builder builder) {
        this.homes = builder.homes;
        this.economy = builder.economy;
        this.presence = builder.presence;
        this.playerstate = builder.playerstate;
        this.kits = builder.kits;
        this.vaults = builder.vaults;
        this.warps = builder.warps;
        this.playerwarps = builder.playerwarps;
        this.moderation = builder.moderation;
        this.teleport = builder.teleport;
        this.vote = builder.vote;
        this.messaging = builder.messaging;
        this.staff = builder.staff;
        this.discordlink = builder.discordlink;
        this.holograms = builder.holograms;
        this.communication = builder.communication;
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

    public Optional<WarpsPlaceholders> warps() {
        return Optional.ofNullable(warps);
    }

    public Optional<PlayerwarpsPlaceholders> playerwarps() {
        return Optional.ofNullable(playerwarps);
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

    public Optional<MessagingPlaceholders> messaging() {
        return Optional.ofNullable(messaging);
    }

    public Optional<StaffPlaceholders> staff() {
        return Optional.ofNullable(staff);
    }

    public Optional<DiscordlinkPlaceholders> discordlink() {
        return Optional.ofNullable(discordlink);
    }

    public Optional<HologramsPlaceholders> holograms() {
        return Optional.ofNullable(holograms);
    }

    public Optional<CommunicationPlaceholders> communication() {
        return Optional.ofNullable(communication);
    }

    /** True when no context registered a seam — registering the expansion would surface nothing. */
    public boolean isEmpty() {
        return homes == null
                && economy == null
                && presence == null
                && playerstate == null
                && kits == null
                && vaults == null
                && warps == null
                && playerwarps == null
                && moderation == null
                && teleport == null
                && vote == null
                && messaging == null
                && staff == null
                && discordlink == null
                && holograms == null
                && communication == null;
    }

    /** Mutable collector for the seams, filled as each context's adapters are wired in bootstrap. */
    public static final class Builder {

        private @Nullable HomesPlaceholders homes;
        private @Nullable EconomyPlaceholders economy;
        private @Nullable PresencePlaceholders presence;
        private @Nullable PlayerstatePlaceholders playerstate;
        private @Nullable KitsPlaceholders kits;
        private @Nullable VaultsPlaceholders vaults;
        private @Nullable WarpsPlaceholders warps;
        private @Nullable PlayerwarpsPlaceholders playerwarps;
        private @Nullable ModerationPlaceholders moderation;
        private @Nullable TeleportPlaceholders teleport;
        private @Nullable VotePlaceholders vote;
        private @Nullable MessagingPlaceholders messaging;
        private @Nullable StaffPlaceholders staff;
        private @Nullable DiscordlinkPlaceholders discordlink;
        private @Nullable HologramsPlaceholders holograms;
        private @Nullable CommunicationPlaceholders communication;

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

        public Builder warps(WarpsPlaceholders seam) {
            this.warps = seam;
            return this;
        }

        public Builder playerwarps(PlayerwarpsPlaceholders seam) {
            this.playerwarps = seam;
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

        public Builder messaging(MessagingPlaceholders seam) {
            this.messaging = seam;
            return this;
        }

        public Builder staff(StaffPlaceholders seam) {
            this.staff = seam;
            return this;
        }

        public Builder discordlink(DiscordlinkPlaceholders seam) {
            this.discordlink = seam;
            return this;
        }

        public Builder holograms(HologramsPlaceholders seam) {
            this.holograms = seam;
            return this;
        }

        public Builder communication(CommunicationPlaceholders seam) {
            this.communication = seam;
            return this;
        }

        public PlaceholderContexts build() {
            return new PlaceholderContexts(this);
        }
    }
}
