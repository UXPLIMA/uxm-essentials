package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.adapter.VerificationController;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableResult;
import com.uxplima.uxmessentials.security.application.DisableTwoFactor;
import com.uxplima.uxmessentials.security.application.EnrollmentChallenge;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.ForceReverification.ForceResult;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.TotpConfirmResult;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /2fa}: the two-factor enrolment command — {@code setup} (generate an authenticator secret and show the
 * {@code otpauth://} URI), {@code confirm <code>} (prove a code, which enables the factor), and {@code disable
 * <code|pin>} (remove it, requiring a current factor). Bare {@code /2fa} reports whether the player is enrolled.
 * The enrolment verbs are gated on {@code uxmessentials.security.2fa}, which ships {@code true} so every player may
 * protect their account. The one exception is {@code force <player>}, an admin verb gated on the op-default
 * {@code uxmessentials.security.force}: it forces a target back into verification (revoking their device trust so
 * their next join re-verifies, and freezing them immediately when they are online), so a {@code requires} gate keeps
 * it hidden from non-admins.
 *
 * <p>The command literal registers as {@code /2fa} but the stable {@link #commandId()} is the letter-first
 * {@code twofactor}: the catalog keys commands by a {@link com.uxplima.uxmessentials.shared.application.command.CommandId}
 * that must begin with a letter, and {@code /twofactor} is offered as an alias. The cheap gating and usage replies go
 * out synchronously; the crypto and DB work (code check, AES encrypt, the {@code security_2fa} read/write) hops onto
 * the {@link Scheduler} off the tick thread. A generated secret is shown only to the enrolling player, never logged.
 */
@NullMarked
public final class TwoFactorCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The self-service permission every player holds to manage their own two-factor. */
    public static final String PERMISSION = "uxmessentials.security.2fa";

    /** The admin permission gating {@code /2fa force <player>}. */
    public static final String FORCE_PERMISSION = "uxmessentials.security.force";

    private final BeginTotpEnrollment begin;
    private final ConfirmTotpEnrollment confirm;
    private final DisableTwoFactor disable;
    private final TwoFactorRepository repository;
    private final SecurityConfig.TwoFactorSettings settings;
    private final ForceReverification forceReverification;
    private final VerificationController verification;
    private final PlayerLookup lookup;
    private final Clock clock;

    public TwoFactorCommand(
            BeginTotpEnrollment begin,
            ConfirmTotpEnrollment confirm,
            DisableTwoFactor disable,
            TwoFactorRepository repository,
            SecurityConfig.TwoFactorSettings settings,
            ForceReverification forceReverification,
            VerificationController verification,
            PlayerLookup lookup,
            Clock clock,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        super(scheduler, messages, sink);
        this.begin = Objects.requireNonNull(begin, "begin");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.disable = Objects.requireNonNull(disable, "disable");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.forceReverification = Objects.requireNonNull(forceReverification, "forceReverification");
        this.verification = Objects.requireNonNull(verification, "verification");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("2fa")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("setup").executes(this::setup))
                .then(Commands.literal("confirm")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_2FA_CONFIRM_USAGE))
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(this::confirmCode)))
                .then(Commands.literal("disable")
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_2FA_DISABLE_USAGE))
                        .then(Commands.argument("factor", StringArgumentType.word())
                                .executes(this::disableFactor)))
                .then(Commands.literal("force")
                        .requires(src -> src.getSender().hasPermission(FORCE_PERMISSION))
                        .executes(ctx -> usage(ctx, SecurityMessageKey.SECURITY_2FA_FORCE_USAGE))
                        .then(CommandSuggestions.playerArgument("player").executes(this::force)))
                .executes(this::status)
                .build();
    }

    @Override
    public String description() {
        return "/2fa setup, confirm <code>, or disable <code|pin> to manage two-factor on your account.";
    }

    @Override
    public String commandId() {
        // CommandId must be letter-first; the live literal is /2fa (see defaultName), with /twofactor as an alias.
        return "twofactor";
    }

    @Override
    public String defaultName() {
        return "2fa";
    }

    @Override
    public List<String> aliases() {
        return List.of("twofactor");
    }

    private int usage(CommandContext<CommandSourceStack> ctx, MessageKey key) {
        reply(ctx.getSource().getSender(), key);
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx.getSource().getSender());
        if (player == null) {
            return 0;
        }
        PlayerRef who = ref(player);
        scheduler.async(() -> {
            boolean enrolled = repository
                    .find(who.uuid())
                    .map(TwoFactorRegistration::hasAnyFactor)
                    .orElse(false);
            notify(who, enrolled ? SecurityMessageKey.SECURITY_2FA_STATUS : SecurityMessageKey.SECURITY_2FA_USAGE);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int setup(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        if (!settings.enabled() || !settings.totp()) {
            reply(sender, SecurityMessageKey.SECURITY_2FA_FEATURE_DISABLED);
            return 0;
        }
        PlayerRef who = ref(player);
        scheduler.async(() -> runSetup(who));
        return Command.SINGLE_SUCCESS;
    }

    private void runSetup(PlayerRef who) {
        if (repository.find(who.uuid()).map(TwoFactorRegistration::totpEnabled).orElse(false)) {
            notify(who, SecurityMessageKey.SECURITY_2FA_ALREADY_ENROLLED);
            return;
        }
        EnrollmentChallenge challenge = begin.begin(who);
        notify(who, SecurityMessageKey.SECURITY_2FA_SETUP_HEADER);
        notify(
                who,
                SecurityMessageKey.SECURITY_2FA_SETUP_SECRET,
                Map.of("secret", challenge.secret().value()));
        notify(who, SecurityMessageKey.SECURITY_2FA_SETUP_URI, Map.of("uri", challenge.otpauthUri()));
        notify(who, SecurityMessageKey.SECURITY_2FA_SETUP_HINT);
    }

    private int confirmCode(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player player = requirePlayer(sender);
        if (player == null) {
            return 0;
        }
        if (!settings.enabled() || !settings.totp()) {
            reply(sender, SecurityMessageKey.SECURITY_2FA_FEATURE_DISABLED);
            return 0;
        }
        String code = ctx.getArgument("code", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> notify(who, confirmMessage(confirm.confirm(who.uuid(), code, clock.instant()))));
        return Command.SINGLE_SUCCESS;
    }

    private int disableFactor(CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx.getSource().getSender());
        if (player == null) {
            return 0;
        }
        String factor = ctx.getArgument("factor", String.class);
        PlayerRef who = ref(player);
        scheduler.async(() -> notify(who, disableMessage(disable.disable(who.uuid(), factor, clock.instant()))));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /2fa force <player>}: force a target (online or offline) to re-verify. The lookup, the durable trust
     * revoke, and the reply all run off the tick thread; when the target is online the verification controller drives
     * them straight back into the freeze. Admin-gated, so a non-admin never reaches here.
     */
    private int force(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef admin = senderRef(sender);
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> runForce(admin, name));
        return Command.SINGLE_SUCCESS;
    }

    private void runForce(PlayerRef admin, String name) {
        Optional<PlayerRef> target = lookup.findByName(name);
        if (target.isEmpty()) {
            notify(admin, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return;
        }
        PlayerRef found = target.get();
        ForceResult result = forceReverification.force(found.uuid());
        if (result == ForceResult.NOT_ENROLLED) {
            notify(admin, SecurityMessageKey.SECURITY_2FA_FORCE_NOT_ENROLLED, Map.of("player", found.name()));
            return;
        }
        // The trust is revoked (next join re-verifies); if the target is online now, freeze them immediately.
        verification.forceReverify(found);
        notify(admin, SecurityMessageKey.SECURITY_2FA_FORCE_DONE, Map.of("player", found.name()));
    }

    /** The ref to reply to: the invoking player, or a stable system ref for a console sender. */
    private static PlayerRef senderRef(CommandSender sender) {
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : new PlayerRef(new UUID(0L, 0L), sender.getName());
    }

    private static MessageKey confirmMessage(TotpConfirmResult result) {
        return switch (result) {
            case ENABLED -> SecurityMessageKey.SECURITY_2FA_ENABLED;
            case NO_PENDING -> SecurityMessageKey.SECURITY_2FA_CONFIRM_NO_PENDING;
            case INVALID_CODE -> SecurityMessageKey.SECURITY_2FA_CONFIRM_INVALID;
        };
    }

    private static MessageKey disableMessage(DisableResult result) {
        return switch (result) {
            case DISABLED -> SecurityMessageKey.SECURITY_2FA_DISABLED;
            case NOT_ENROLLED -> SecurityMessageKey.SECURITY_2FA_DISABLE_NOT_ENROLLED;
            case INVALID_FACTOR -> SecurityMessageKey.SECURITY_2FA_DISABLE_INVALID;
            case LOCKED_OUT -> SecurityMessageKey.SECURITY_2FA_DISABLE_LOCKED_OUT;
        };
    }
}
