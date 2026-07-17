package com.uxplima.uxmessentials.security.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.security.IpGuardStores;
import com.uxplima.uxmessentials.persistence.security.TrustStores;
import com.uxplima.uxmessentials.persistence.security.TwoFactorRepositories;
import com.uxplima.uxmessentials.security.adapter.inbound.command.ClientInfoCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.IpAltsCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.PinCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadListener;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.ReauthCommandListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityGuardListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityJoinListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableTwoFactor;
import com.uxplima.uxmessentials.security.application.FindAlts;
import com.uxplima.uxmessentials.security.application.PendingTotpEnrollments;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the security context's two-factor store, enrolment use cases, and the {@code /2fa} and {@code /pin}
 * commands over the injected kernel ports and the shared persistence DSL. The store is the jOOQ
 * {@code TwoFactorRepository} (built through the security persistence factory, so no jOOQ type reaches this layer),
 * which hashes the PIN and encrypts the TOTP secret under an AES key-file kept beside the module's config. The
 * pending, un-confirmed TOTP secrets are transient in-memory state held in {@link PendingTotpEnrollments}, cleared on
 * {@link Wired#stop()} so a disable or reload leaves no residual secret.
 */
@NullMarked
public final class SecurityWiring {

    private SecurityWiring() {}

    /** Build the security use cases, commands, and join-verification listeners from {@code ctx} and persistence. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, TextInput textInput) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(textInput, "textInput");
        KernelPorts kernel = ctx.kernel();
        SecurityConfig config = SecurityConfig.from(ctx.config());
        SecurityConfig.TwoFactorSettings twoFactor = config.twoFactor();
        Path keyFile = plugin.getDataFolder().toPath().resolve("modules/security/secret.key");
        TwoFactorRepository repository = TwoFactorRepositories.jooq(persistence, keyFile);
        PendingTotpEnrollments pending = new PendingTotpEnrollments();
        BeginTotpEnrollment begin = new BeginTotpEnrollment(new SecretGenerator(), pending, twoFactor.issuer());
        ConfirmTotpEnrollment confirm = new ConfirmTotpEnrollment(repository, pending, twoFactor.codeWindow());
        DisableTwoFactor disable = new DisableTwoFactor(repository, twoFactor.codeWindow());
        SetPin setPin = new SetPin(repository, twoFactor.pinPolicy());
        Clock clock = Clock.systemUTC();

        // Phase 4 — IP/alt guard + ClientID: the DB-backed IP association store (hashed tokens only, never a raw
        // address, so alts survive a restart), the session-only client-brand registry, the staff notifier that fans
        // an alt / flagged-client notice to the notify-perm holders and mirrors it to the log, and the two join
        // guards. Each guard self-gates on its own config flag; the /ipalts and /clientinfo staff reads are published
        // alongside the enrolment verbs.
        IpGuardStore ipGuardStore = IpGuardStores.jooq(persistence);
        ClientBrandRegistry brands = new ClientBrandRegistry();
        SecurityStaffNotifier staffNotifier = new SecurityStaffNotifier(
                plugin.getServer(), kernel.scheduler(), kernel.messages(), kernel.messageSink(), kernel.log());
        IpGuardController ipGuardController = new IpGuardController(
                ipGuardStore,
                config.ipGuard(),
                kernel.playerLookup(),
                staffNotifier,
                kernel.scheduler(),
                kernel.messages(),
                clock);
        ClientGuard clientGuard =
                new ClientGuard(brands, config.clientId(), staffNotifier, kernel.scheduler(), kernel.messages());
        FindAlts findAlts = new FindAlts(ipGuardStore);

        List<CommandRegistration> commands = List.of(
                new TwoFactorCommand(
                        begin,
                        confirm,
                        disable,
                        repository,
                        twoFactor,
                        clock,
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new PinCommand(setPin, twoFactor, kernel.scheduler(), kernel.messages(), kernel.messageSink()),
                new IpAltsCommand(
                        findAlts, kernel.playerLookup(), kernel.scheduler(), kernel.messages(), kernel.messageSink()),
                new ClientInfoCommand(
                        brands, kernel.playerLookup(), kernel.scheduler(), kernel.messages(), kernel.messageSink()));

        // Phase 2 — join verification: the DB-backed device-trust store, the transient freeze/lockout registry, the
        // keypad GUI, and the controller that decides on join and drives every submitted PIN/code to an unfreeze,
        // re-prompt, or lockout kick. A submitted code is verified through VerifyTwoFactor (TOTP or PIN), off-thread.
        TrustStore trustStore = TrustStores.jooq(persistence);
        VerificationSessions sessions = new VerificationSessions();
        ReauthState reauthState = new ReauthState();
        ReauthSessions reauthSessions = new ReauthSessions();
        PinKeypadView keypad = new PinKeypadView(kernel.messages(), kernel.scheduler());
        VerifyTwoFactor verify = new VerifyTwoFactor(repository, twoFactor.codeWindow());
        TextInputTotpPrompt totpPrompt = new TextInputTotpPrompt(textInput);
        VerificationController controller = new VerificationController(
                repository,
                verify,
                trustStore,
                sessions,
                reauthState,
                config.joinVerification(),
                keypad,
                totpPrompt,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                clock);

        // Phase 3 — op-command protection: a re-auth controller sharing the same keypad, a router that sends each
        // keypad submission to whichever flow the player is in, and the command listener that blocks a protected
        // command until the player's re-auth window is fresh. The recent-verify check is in-memory; the verify is
        // off-thread. The op-protection sub-feature is gated by its own enabled flag, so a no-op costs nothing.
        SecurityConfig.OpProtection opProtection = config.opProtection();
        ReauthController reauthController = new ReauthController(
                repository,
                verify,
                reauthState,
                reauthSessions,
                keypad,
                totpPrompt,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                clock);
        KeypadRouter router = new KeypadRouter(reauthSessions, controller, reauthController);
        List<Listener> listeners = List.of(
                new SecurityJoinListener(controller),
                new VerificationFreezeListener(sessions, kernel.messages(), kernel.messageSink()),
                new PinKeypadListener(keypad, router, sessions),
                new ReauthCommandListener(
                        opProtection.enabled(), opProtection.policy(), reauthState, reauthController, clock),
                new SecurityGuardListener(ipGuardController, clientGuard, brands));
        return new Wired(commands, listeners, pending, sessions, keypad, reauthSessions, reauthState, brands);
    }

    /**
     * Everything the security module contributes once wired: the {@code /2fa} and {@code /pin} command registrations
     * and the join-verification listeners to publish, plus the transient registries cleared on stop (the pending
     * un-confirmed enrolments, the freeze/lockout sessions) and the keypad view whose open windows close on stop — so
     * a disable or reload leaves no residual secret and no locked player.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/freeze/keypad/op-protection Bukkit listeners to register
     * @param pending the pending un-confirmed TOTP enrolments, cleared on module stop
     * @param sessions the freeze/lockout registry, cleared on module stop
     * @param keypad the keypad view whose open windows close on module stop
     * @param reauthSessions the in-flight op-command re-auth prompts, cleared on module stop
     * @param reauthState the per-player last-verified stamps, cleared on module stop
     * @param brands the session-only client-brand registry, cleared on module stop
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PendingTotpEnrollments pending,
            VerificationSessions sessions,
            PinKeypadView keypad,
            ReauthSessions reauthSessions,
            ReauthState reauthState,
            ClientBrandRegistry brands) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(keypad, "keypad");
            Objects.requireNonNull(reauthSessions, "reauthSessions");
            Objects.requireNonNull(reauthState, "reauthState");
            Objects.requireNonNull(brands, "brands");
        }

        /** Drop every pending secret, freeze and re-auth window, close every keypad, and forget every recorded brand. */
        public void stop() {
            pending.clearAll();
            keypad.closeAll();
            sessions.clearAll();
            reauthSessions.clearAll();
            reauthState.clearAll();
            brands.clearAll();
        }
    }
}
