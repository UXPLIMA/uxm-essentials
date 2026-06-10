package com.uxplima.uxmessentials.moderation.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.ClearWarns;
import com.uxplima.uxmessentials.moderation.application.CommandSpy;
import com.uxplima.uxmessentials.moderation.application.DelJail;
import com.uxplima.uxmessentials.moderation.application.Freeze;
import com.uxplima.uxmessentials.moderation.application.IssueWarn;
import com.uxplima.uxmessentials.moderation.application.Jail;
import com.uxplima.uxmessentials.moderation.application.JailCountdown;
import com.uxplima.uxmessentials.moderation.application.Kick;
import com.uxplima.uxmessentials.moderation.application.KickAll;
import com.uxplima.uxmessentials.moderation.application.ListAlts;
import com.uxplima.uxmessentials.moderation.application.ListBans;
import com.uxplima.uxmessentials.moderation.application.ListJailed;
import com.uxplima.uxmessentials.moderation.application.ListJails;
import com.uxplima.uxmessentials.moderation.application.ListMutes;
import com.uxplima.uxmessentials.moderation.application.LoginEnforcement;
import com.uxplima.uxmessentials.moderation.application.Mute;
import com.uxplima.uxmessentials.moderation.application.ReviewBanHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewMuteHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewWarns;
import com.uxplima.uxmessentials.moderation.application.SanctionSummary;
import com.uxplima.uxmessentials.moderation.application.Seen;
import com.uxplima.uxmessentials.moderation.application.SetJail;
import com.uxplima.uxmessentials.moderation.application.TempBan;
import com.uxplima.uxmessentials.moderation.application.TempBanIp;
import com.uxplima.uxmessentials.moderation.application.TempWarn;
import com.uxplima.uxmessentials.moderation.application.ToggleJail;
import com.uxplima.uxmessentials.moderation.application.Unban;
import com.uxplima.uxmessentials.moderation.application.UnbanIp;
import com.uxplima.uxmessentials.moderation.application.Unjail;
import com.uxplima.uxmessentials.moderation.application.Unmute;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed moderation use cases plus the lookups the inbound adapter needs, bundled so each Brigadier
 * command and each listener takes one collaborator rather than a dozen. Built once in {@link ModerationWiring}
 * and shared read-only across the command surface and the login/join listeners.
 */
@NullMarked
public final class ModerationServices {

    private final Mute mute;
    private final Unmute unmute;
    private final Jail jail;
    private final Unjail unjail;
    private final ToggleJail toggleJail;
    private final TempBan tempBan;
    private final Ban ban;
    private final Unban unban;
    private final Kick kick;
    private final KickAll kickAll;
    private final IssueWarn warn;
    private final TempWarn tempWarn;
    private final ReviewWarns reviewWarns;
    private final ClearWarns clearWarns;
    private final SanctionSummary sanctionSummary;
    private final ListJails listJails;
    private final ListJailed listJailed;
    private final SetJail setJail;
    private final DelJail delJail;
    private final ListBans listBans;
    private final ListMutes listMutes;
    private final ReviewBanHistory reviewBanHistory;
    private final ReviewMuteHistory reviewMuteHistory;
    private final BanIp banIp;
    private final TempBanIp tempBanIp;
    private final UnbanIp unbanIp;
    private final Freeze freeze;
    private final Seen seen;
    private final ListAlts listAlts;
    private final CommandSpy commandSpy;
    private final JailCountdown jailCountdown;
    private final LoginEnforcement loginEnforcement;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final TargetResolver targets;

    ModerationServices(Builder builder) {
        this.mute = Objects.requireNonNull(builder.mute, "mute");
        this.unmute = Objects.requireNonNull(builder.unmute, "unmute");
        this.jail = Objects.requireNonNull(builder.jail, "jail");
        this.unjail = Objects.requireNonNull(builder.unjail, "unjail");
        this.toggleJail = Objects.requireNonNull(builder.toggleJail, "toggleJail");
        this.tempBan = Objects.requireNonNull(builder.tempBan, "tempBan");
        this.ban = Objects.requireNonNull(builder.ban, "ban");
        this.unban = Objects.requireNonNull(builder.unban, "unban");
        this.kick = Objects.requireNonNull(builder.kick, "kick");
        this.kickAll = Objects.requireNonNull(builder.kickAll, "kickAll");
        this.warn = Objects.requireNonNull(builder.warn, "warn");
        this.tempWarn = Objects.requireNonNull(builder.tempWarn, "tempWarn");
        this.reviewWarns = Objects.requireNonNull(builder.reviewWarns, "reviewWarns");
        this.clearWarns = Objects.requireNonNull(builder.clearWarns, "clearWarns");
        this.sanctionSummary = Objects.requireNonNull(builder.sanctionSummary, "sanctionSummary");
        this.listJails = Objects.requireNonNull(builder.listJails, "listJails");
        this.listJailed = Objects.requireNonNull(builder.listJailed, "listJailed");
        this.setJail = Objects.requireNonNull(builder.setJail, "setJail");
        this.delJail = Objects.requireNonNull(builder.delJail, "delJail");
        this.listBans = Objects.requireNonNull(builder.listBans, "listBans");
        this.listMutes = Objects.requireNonNull(builder.listMutes, "listMutes");
        this.reviewBanHistory = Objects.requireNonNull(builder.reviewBanHistory, "reviewBanHistory");
        this.reviewMuteHistory = Objects.requireNonNull(builder.reviewMuteHistory, "reviewMuteHistory");
        this.banIp = Objects.requireNonNull(builder.banIp, "banIp");
        this.tempBanIp = Objects.requireNonNull(builder.tempBanIp, "tempBanIp");
        this.unbanIp = Objects.requireNonNull(builder.unbanIp, "unbanIp");
        this.freeze = Objects.requireNonNull(builder.freeze, "freeze");
        this.seen = Objects.requireNonNull(builder.seen, "seen");
        this.listAlts = Objects.requireNonNull(builder.listAlts, "listAlts");
        this.commandSpy = Objects.requireNonNull(builder.commandSpy, "commandSpy");
        this.jailCountdown = Objects.requireNonNull(builder.jailCountdown, "jailCountdown");
        this.loginEnforcement = Objects.requireNonNull(builder.loginEnforcement, "loginEnforcement");
        this.repository = Objects.requireNonNull(builder.repository, "repository");
        this.players = Objects.requireNonNull(builder.players, "players");
        this.targets = Objects.requireNonNull(builder.targets, "targets");
    }

    public Mute mute() {
        return mute;
    }

    public Unmute unmute() {
        return unmute;
    }

    public Jail jail() {
        return jail;
    }

    public Unjail unjail() {
        return unjail;
    }

    public ToggleJail toggleJail() {
        return toggleJail;
    }

    public TempBan tempBan() {
        return tempBan;
    }

    public Ban ban() {
        return ban;
    }

    public Unban unban() {
        return unban;
    }

    public Kick kick() {
        return kick;
    }

    public KickAll kickAll() {
        return kickAll;
    }

    public IssueWarn warn() {
        return warn;
    }

    public TempWarn tempWarn() {
        return tempWarn;
    }

    public ReviewWarns reviewWarns() {
        return reviewWarns;
    }

    public ClearWarns clearWarns() {
        return clearWarns;
    }

    public SanctionSummary sanctionSummary() {
        return sanctionSummary;
    }

    public ListJails listJails() {
        return listJails;
    }

    public ListJailed listJailed() {
        return listJailed;
    }

    public SetJail setJail() {
        return setJail;
    }

    public DelJail delJail() {
        return delJail;
    }

    public ListBans listBans() {
        return listBans;
    }

    public ListMutes listMutes() {
        return listMutes;
    }

    public ReviewBanHistory reviewBanHistory() {
        return reviewBanHistory;
    }

    public ReviewMuteHistory reviewMuteHistory() {
        return reviewMuteHistory;
    }

    public BanIp banIp() {
        return banIp;
    }

    public TempBanIp tempBanIp() {
        return tempBanIp;
    }

    public UnbanIp unbanIp() {
        return unbanIp;
    }

    public Freeze freeze() {
        return freeze;
    }

    public Seen seen() {
        return seen;
    }

    public ListAlts listAlts() {
        return listAlts;
    }

    public CommandSpy commandSpy() {
        return commandSpy;
    }

    public JailCountdown jailCountdown() {
        return jailCountdown;
    }

    public LoginEnforcement loginEnforcement() {
        return loginEnforcement;
    }

    public ModerationRepository repository() {
        return repository;
    }

    public PlayerLookup players() {
        return players;
    }

    public TargetResolver targets() {
        return targets;
    }

    /** Step builder so {@link ModerationWiring} assembles the bundle field-by-field without a 17-arg call. */
    @NullMarked
    static final class Builder {
        private @org.jspecify.annotations.Nullable Mute mute;
        private @org.jspecify.annotations.Nullable Unmute unmute;
        private @org.jspecify.annotations.Nullable Jail jail;
        private @org.jspecify.annotations.Nullable Unjail unjail;
        private @org.jspecify.annotations.Nullable ToggleJail toggleJail;
        private @org.jspecify.annotations.Nullable TempBan tempBan;
        private @org.jspecify.annotations.Nullable Ban ban;
        private @org.jspecify.annotations.Nullable Unban unban;
        private @org.jspecify.annotations.Nullable Kick kick;
        private @org.jspecify.annotations.Nullable KickAll kickAll;
        private @org.jspecify.annotations.Nullable IssueWarn warn;
        private @org.jspecify.annotations.Nullable TempWarn tempWarn;
        private @org.jspecify.annotations.Nullable ReviewWarns reviewWarns;
        private @org.jspecify.annotations.Nullable ClearWarns clearWarns;
        private @org.jspecify.annotations.Nullable SanctionSummary sanctionSummary;
        private @org.jspecify.annotations.Nullable ListJails listJails;
        private @org.jspecify.annotations.Nullable ListJailed listJailed;
        private @org.jspecify.annotations.Nullable SetJail setJail;
        private @org.jspecify.annotations.Nullable DelJail delJail;
        private @org.jspecify.annotations.Nullable ListBans listBans;
        private @org.jspecify.annotations.Nullable ListMutes listMutes;
        private @org.jspecify.annotations.Nullable ReviewBanHistory reviewBanHistory;
        private @org.jspecify.annotations.Nullable ReviewMuteHistory reviewMuteHistory;
        private @org.jspecify.annotations.Nullable BanIp banIp;
        private @org.jspecify.annotations.Nullable TempBanIp tempBanIp;
        private @org.jspecify.annotations.Nullable UnbanIp unbanIp;
        private @org.jspecify.annotations.Nullable Freeze freeze;
        private @org.jspecify.annotations.Nullable Seen seen;
        private @org.jspecify.annotations.Nullable ListAlts listAlts;
        private @org.jspecify.annotations.Nullable CommandSpy commandSpy;
        private @org.jspecify.annotations.Nullable JailCountdown jailCountdown;
        private @org.jspecify.annotations.Nullable LoginEnforcement loginEnforcement;
        private @org.jspecify.annotations.Nullable ModerationRepository repository;
        private @org.jspecify.annotations.Nullable PlayerLookup players;
        private @org.jspecify.annotations.Nullable TargetResolver targets;

        Builder mute(Mute value) {
            this.mute = value;
            return this;
        }

        Builder unmute(Unmute value) {
            this.unmute = value;
            return this;
        }

        Builder jail(Jail value) {
            this.jail = value;
            return this;
        }

        Builder unjail(Unjail value) {
            this.unjail = value;
            return this;
        }

        Builder toggleJail(ToggleJail value) {
            this.toggleJail = value;
            return this;
        }

        Builder tempBan(TempBan value) {
            this.tempBan = value;
            return this;
        }

        Builder ban(Ban value) {
            this.ban = value;
            return this;
        }

        Builder unban(Unban value) {
            this.unban = value;
            return this;
        }

        Builder kick(Kick value) {
            this.kick = value;
            return this;
        }

        Builder kickAll(KickAll value) {
            this.kickAll = value;
            return this;
        }

        Builder warn(IssueWarn value) {
            this.warn = value;
            return this;
        }

        Builder tempWarn(TempWarn value) {
            this.tempWarn = value;
            return this;
        }

        Builder reviewWarns(ReviewWarns value) {
            this.reviewWarns = value;
            return this;
        }

        Builder clearWarns(ClearWarns value) {
            this.clearWarns = value;
            return this;
        }

        Builder sanctionSummary(SanctionSummary value) {
            this.sanctionSummary = value;
            return this;
        }

        Builder listJails(ListJails value) {
            this.listJails = value;
            return this;
        }

        Builder listJailed(ListJailed value) {
            this.listJailed = value;
            return this;
        }

        Builder setJail(SetJail value) {
            this.setJail = value;
            return this;
        }

        Builder delJail(DelJail value) {
            this.delJail = value;
            return this;
        }

        Builder listBans(ListBans value) {
            this.listBans = value;
            return this;
        }

        Builder listMutes(ListMutes value) {
            this.listMutes = value;
            return this;
        }

        Builder reviewBanHistory(ReviewBanHistory value) {
            this.reviewBanHistory = value;
            return this;
        }

        Builder reviewMuteHistory(ReviewMuteHistory value) {
            this.reviewMuteHistory = value;
            return this;
        }

        Builder banIp(BanIp value) {
            this.banIp = value;
            return this;
        }

        Builder tempBanIp(TempBanIp value) {
            this.tempBanIp = value;
            return this;
        }

        Builder unbanIp(UnbanIp value) {
            this.unbanIp = value;
            return this;
        }

        Builder freeze(Freeze value) {
            this.freeze = value;
            return this;
        }

        Builder seen(Seen value) {
            this.seen = value;
            return this;
        }

        Builder listAlts(ListAlts value) {
            this.listAlts = value;
            return this;
        }

        Builder commandSpy(CommandSpy value) {
            this.commandSpy = value;
            return this;
        }

        Builder jailCountdown(JailCountdown value) {
            this.jailCountdown = value;
            return this;
        }

        Builder loginEnforcement(LoginEnforcement value) {
            this.loginEnforcement = value;
            return this;
        }

        Builder repository(ModerationRepository value) {
            this.repository = value;
            return this;
        }

        Builder players(PlayerLookup value) {
            this.players = value;
            return this;
        }

        Builder targets(TargetResolver value) {
            this.targets = value;
            return this;
        }

        ModerationServices build() {
            // The constructor validates every field is present; a missing collaborator fails fast there.
            return new ModerationServices(this);
        }
    }
}
