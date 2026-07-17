package com.uxplima.uxmessentials.security.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.security.TwoFactorRepositories;
import com.uxplima.uxmessentials.security.adapter.inbound.command.PinCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableTwoFactor;
import com.uxplima.uxmessentials.security.application.PendingTotpEnrollments;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
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

    /** Build the security use cases and commands from {@code ctx} and the shared {@code persistence} handle. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
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
                new PinCommand(setPin, twoFactor, kernel.scheduler(), kernel.messages(), kernel.messageSink()));
        return new Wired(commands, pending);
    }

    /**
     * Everything the security module contributes once wired: the {@code /2fa} and {@code /pin} command
     * registrations to publish, and the pending-enrolment registry (cleared on stop so a disable or reload leaves no
     * un-confirmed secret in memory).
     *
     * @param commands the Brigadier command registrations to publish
     * @param pending the pending un-confirmed TOTP enrolments, cleared on module stop
     */
    public record Wired(List<CommandRegistration> commands, PendingTotpEnrollments pending) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(pending, "pending");
        }

        /** Drop every pending un-confirmed enrolment, so a disable or reload leaves no residual secret in memory. */
        public void stop() {
            pending.clearAll();
        }
    }
}
