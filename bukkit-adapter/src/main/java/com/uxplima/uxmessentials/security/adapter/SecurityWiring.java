package com.uxplima.uxmessentials.security.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.security.TrustStores;
import com.uxplima.uxmessentials.persistence.security.TwoFactorRepositories;
import com.uxplima.uxmessentials.security.adapter.inbound.command.ClientInfoCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.IpAltsCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.PinCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.SecurityCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadWindowListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.PinEntryChatGuard;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.ReauthCommandListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityGuardListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.SecurityJoinListener;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.BeginTotpEnrollment;
import com.uxplima.uxmessentials.security.application.ChangePin;
import com.uxplima.uxmessentials.security.application.ConfirmTotpEnrollment;
import com.uxplima.uxmessentials.security.application.DisableTotp;
import com.uxplima.uxmessentials.security.application.FindAlts;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.security.application.PendingTotpEnrollments;
import com.uxplima.uxmessentials.security.application.RemovePin;
import com.uxplima.uxmessentials.security.application.ResetFactors;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SessionCarry;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import com.uxplima.uxmessentials.security.domain.SecretGenerator;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.ip.IpHistoryRecorder;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.StaffActions;
import com.uxplima.uxmessentials.shared.application.RelayAddresses;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the security context's two-factor store, enrolment use cases, and the {@code /2fa}, {@code /pin} and
 * {@code /security} commands over the injected kernel ports and the shared persistence DSL. The store is the jOOQ
 * {@code TwoFactorRepository} (built through the security persistence factory, so no jOOQ type reaches this layer),
 * which hashes the PIN and encrypts the TOTP secret under an AES key-file kept beside the module's config. The
 * pending, un-confirmed TOTP secrets are transient in-memory state held in {@link PendingTotpEnrollments}, cleared on
 * {@link Wired#stop()} so a disable or reload leaves no residual secret.
 */
@NullMarked
public final class SecurityWiring {

    private SecurityWiring() {}

    /** Build the security use cases, commands, and join-verification listeners from {@code ctx} and persistence. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            SecureEntryInput entryInput,
            Menus menus,
            MenuBindings menuBindings,
            LockoutBan lockoutBan,
            ServerConnector proxy,
            IpHistoryStore ipHistory,
            IpTokens ipTokens,
            IpHistoryRecorder recorder,
            RelayAddresses relays,
            Network network) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(entryInput, "entryInput");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        Objects.requireNonNull(lockoutBan, "lockoutBan");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(ipHistory, "ipHistory");
        Objects.requireNonNull(ipTokens, "ipTokens");
        Objects.requireNonNull(recorder, "recorder");
        Objects.requireNonNull(relays, "relays");
        Objects.requireNonNull(network, "network");
        KernelPorts kernel = ctx.kernel();
        SecurityConfig config = SecurityConfig.from(ctx.config(), kernel.log());
        SecurityConfig.TwoFactorSettings twoFactor = config.twoFactor();
        Path keyFile = plugin.getDataFolder().toPath().resolve("modules/security/secret.key");
        TwoFactorRepository repository = TwoFactorRepositories.jooq(persistence, keyFile);
        PendingTotpEnrollments pending = new PendingTotpEnrollments();
        // The durable, account-scoped brute-force limiter is shared by every verification surface: the join freeze,
        // the op re-auth keypad, /2fa disable, /pin change and /pin remove. A failed proof on any of them counts
        // toward, and a lockout blocks, all of them, on every backend of the database. Built from the
        // join-verification lockout policy and duration.
        // A lockout also drops any proof the player could carry to another backend. The drop joins that player's other
        // session writes on the lane, so a proof still being recorded cannot land after it and bring the session back.
        VerifiedSessions verifiedSessions = TrustStores.sessions(persistence);
        PlayerLane sessionLane = new PlayerLane(kernel.scheduler(), kernel.log());
        AttemptLimiter limiter = new AttemptLimiter(
                TrustStores.attempts(persistence),
                player -> sessionLane.run(
                        player, "security_session_revoke_failed", () -> verifiedSessions.revoke(player)),
                config.joinVerification().lockoutPolicy(),
                config.joinVerification().lockout());
        BeginTotpEnrollment begin = new BeginTotpEnrollment(new SecretGenerator(), pending, twoFactor.issuer());
        ConfirmTotpEnrollment confirm = new ConfirmTotpEnrollment(repository, pending, twoFactor.codeWindow());
        DisableTotp disable = new DisableTotp(repository, limiter, twoFactor.codeWindow());
        SetPin setPin = new SetPin(repository, twoFactor.pinPolicy());
        ChangePin changePin = new ChangePin(repository, limiter, twoFactor.pinPolicy());
        RemovePin removePin = new RemovePin(repository, limiter);
        Clock clock = Clock.systemUTC();

        // IP/alt guard and ClientID. The associations come from the kernel IP-history store (tokens only on
        // the read side, so this guard never sees an address) and the kernel recorder that writes them; the guard
        // watches that recorder rather than capturing a join of its own. Alongside it are the session-only
        // client-brand registry, the staff notifier that fans an alt / flagged-client notice to the notify-perm
        // holders and mirrors it to the log, and the client guard. Each guard self-gates on its own config flag;
        // the /ipalts and /clientinfo staff reads are published alongside the enrolment verbs.
        ClientBrandRegistry brands = new ClientBrandRegistry();
        StaffActions staffActions = new StaffActions(network.bus().publisher());
        SecurityStaffNotifier staffNotifier = new SecurityStaffNotifier(
                plugin.getServer(),
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                kernel.log(),
                staffActions);
        network.bus().registry().register(staffNotifier.listener());
        IpGuardController ipGuardController = new IpGuardController(
                ipHistory,
                config.ipGuard(),
                kernel.playerLookup(),
                staffNotifier,
                kernel.scheduler(),
                kernel.messages());
        recorder.observe(ipGuardController);
        ClientGuard clientGuard =
                new ClientGuard(brands, config.clientId(), staffNotifier, kernel.scheduler(), kernel.messages());
        FindAlts findAlts = new FindAlts(ipHistory);

        // Join verification: the DB-backed device-trust store, the transient freeze/lockout registry, the
        // keypad GUI, and the controller that decides on join and drives every submitted PIN/code to an unfreeze,
        // re-prompt, or lockout kick. A submitted code is verified through VerifyTwoFactor (TOTP or PIN), off-thread.
        // Built ahead of the commands so /2fa force can drive an online target straight back into the freeze.
        TrustStore trustStore = TrustStores.jooq(persistence);
        VerificationSessions sessions = new VerificationSessions();
        ReauthState reauthState = new ReauthState();
        ReauthSessions reauthSessions = new ReauthSessions();
        PinEnrolmentSessions enrolmentSessions = new PinEnrolmentSessions();
        // The titles over the keypad and the sounds each step makes, shared by the join freeze and the op re-auth so
        // both flows feel the same. The keypad plays the tap sound itself; the controller plays the outcomes.
        VerificationFeedback feedback =
                new VerificationFeedback(config.feedback(), kernel.scheduler(), kernel.messages(), kernel.log());
        // A Bedrock player is shown menus as forms, which cannot carry a pad that redraws after each tap, so they
        // type the whole entry into one text prompt instead; a Java player keeps the pad.
        PinKeypadView keypad = new PinKeypadView(
                menus,
                new TextInputPinPrompt(entryInput),
                sessions::isPending,
                id -> sessions.isPending(id) || reauthSessions.isPending(id),
                kernel.messages(),
                feedback,
                kernel.scheduler(),
                kernel.log());
        FreezeGameMode freezeGameMode =
                new FreezeGameMode(plugin, config.joinVerification().spectator());
        // The module's own two teleports (into the holding area and back out) announce themselves here, because the
        // freeze cancels teleports and a teleport event names a cause, never an author.
        FreezeTeleports ownTeleports = new FreezeTeleports();
        // Resolved lazily: the plugin enables before the worlds exist, so parsing here would look up a world that
        // is not there yet and turn a good configured value into an "unknown world" warning. Bootstrap warms it
        // once the worlds are up, which is where the warning for a genuinely bad value belongs.
        FreezeHoldingArea holdingArea = new FreezeHoldingArea(
                () -> FreezeHoldingArea.parse(config.joinVerification().holdingArea(), kernel.log())
                        .orElse(null),
                ownTeleports,
                kernel.log());
        VerifyTwoFactor verify = new VerifyTwoFactor(repository, twoFactor.codeWindow());
        // One check in flight per player across the join freeze and the op re-auth, each reserved on the budget first.
        ProofChecks checks = new ProofChecks(verify, limiter, kernel.log(), clock);
        TextInputTotpPrompt totpPrompt = new TextInputTotpPrompt(entryInput);
        // The create-a-PIN pad: shown to a player holding uxmessentials.security.pin.required who has no factor, so
        // "you must have a PIN" is enforceable rather than advisory. It writes through the same SetPin the /pin
        // command uses, so a PIN made here obeys the same length and blocked-list rules.
        // Built after the enrolment controller it hands players back to, so the enrolment reaches it through this.
        AtomicReference<VerificationController> verification = new AtomicReference<>();
        PinEnrolmentController enrolment = new PinEnrolmentController(
                setPin,
                twoFactor.pinPolicy(),
                enrolmentSessions,
                sessions,
                keypad,
                feedback,
                freezeGameMode,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                ref -> {
                    VerificationController built = verification.get();
                    if (built != null) {
                        built.forceReverify(ref);
                    }
                });
        // A proof carries across a switch only on a network; off it every join is a login and proves the factor.
        SessionCarry carry = new SessionCarry(
                verifiedSessions,
                network.enabled() ? config.joinVerification().switchGrace() : java.time.Duration.ZERO);
        String serverId = network.bus().publisher().serverId();
        ProxyArrivals arrivals = new ProxyArrivals(
                kernel.scheduler(),
                network.bus().registry().receipts(),
                serverId,
                network.proxyAnswering(),
                carry,
                kernel.log(),
                clock);
        network.bus().registry().register(arrivals);
        // Nobody is on a backend that just started, so a session it held from before is released now. One it records
        // or takes from here on carries a later time and stays, so joins need not wait for this.
        java.time.Instant started = clock.instant();
        if (carry.enabled()) {
            kernel.scheduler().async(() -> releaseAtStart(kernel, carry, serverId, started, 1));
        }
        VerificationController controller = new VerificationController(
                repository,
                checks,
                trustStore,
                sessions,
                limiter,
                reauthState,
                config.joinVerification(),
                keypad,
                totpPrompt,
                freezeGameMode,
                feedback,
                lockoutBan,
                enrolment,
                kernel.permissions(),
                holdingArea,
                proxy,
                ipTokens,
                relays,
                kernel.events(),
                carry,
                arrivals,
                serverId,
                network.presence(),
                sessionLane,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                kernel.log(),
                clock);
        verification.set(controller);
        // The two /security operator verbs. Force revokes the target's device trust (the durable forced state, so
        // their next join re-verifies even from a trusted device), paired with the immediate online freeze driven
        // through the controller above. Reset is the recovery door: the one path that clears a factor without proving
        // it, for a player who has lost their authenticator or forgotten their PIN.
        ForceReverification forceReverification = new ForceReverification(repository, trustStore, verifiedSessions);
        ResetFactors resetFactors = new ResetFactors(repository, trustStore, verifiedSessions);
        // Their live half reaches a target on another backend: the backend holding the player carries it out.
        NetworkVerification networkVerification = new NetworkVerification(controller, network.presence(), staffActions);
        for (RemoteSyncListener listener : networkVerification.listeners()) {
            network.bus().registry().register(listener);
        }
        // /clientinfo on a player on another backend asks that backend, which holds the brand their client reported.
        NetworkClientBrands clientBrands =
                new NetworkClientBrands(brands, network.presence(), staffActions, kernel.scheduler());
        for (RemoteSyncListener listener : clientBrands.listeners()) {
            network.bus().registry().register(listener);
        }

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
                new PinCommand(
                        setPin,
                        changePin,
                        removePin,
                        repository,
                        twoFactor,
                        (player, viewer) -> controller.forceReverify(viewer),
                        clock,
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new SecurityCommand(
                        repository,
                        forceReverification,
                        resetFactors,
                        networkVerification,
                        carry,
                        kernel.playerLookup(),
                        kernel.log(),
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()),
                new IpAltsCommand(
                        findAlts, kernel.playerLookup(), kernel.scheduler(), kernel.messages(), kernel.messageSink()),
                new ClientInfoCommand(
                        clientBrands,
                        kernel.playerLookup(),
                        kernel.scheduler(),
                        kernel.messages(),
                        kernel.messageSink()));

        // Op-command protection: a re-auth controller sharing the same keypad, a router that sends each
        // keypad submission to whichever flow the player is in, and the command listener that blocks a protected
        // command until the player's re-auth window is fresh. The recent-verify check is in-memory; the verify is
        // off-thread. The op-protection sub-feature is gated by its own enabled flag, so a no-op costs nothing.
        SecurityConfig.OpProtection opProtection = config.opProtection();
        ReauthController reauthController = new ReauthController(
                repository,
                checks,
                limiter,
                reauthState,
                reauthSessions,
                keypad,
                totpPrompt,
                kernel.scheduler(),
                kernel.messages(),
                kernel.messageSink(),
                clock);
        KeypadRouter router =
                new KeypadRouter(reauthSessions, enrolmentSessions, sessions, controller, reauthController, enrolment);
        // The keypad renders through the menu engine: register its per-button actions (routed to whichever verify flow
        // the player is in), its masked-display / digit-label placeholders and its spec now that the router exists. The
        // close listener is the one behaviour the engine does not model: reopening the window if a still-frozen player
        // escapes it.
        keypad.register(menuBindings, router, plugin.getDataFolder().toPath(), kernel.log());
        // On an offline-mode server the login plugin owns the first half of the login, so the second factor waits for
        // it rather than racing it. The hook is reflective and finds nothing on a server with no login plugin, which
        // leaves the plain join hook in charge.
        LoginPluginHandoff handoff = new LoginPluginHandoff(plugin, kernel.log());
        if (config.joinVerification().waitForLoginPlugin() && handoff.hook(controller::onAuthenticated)) {
            controller.deferToLoginPlugin();
        }
        List<Listener> listeners = List.of(
                new SecurityJoinListener(controller),
                new VerificationFreezeListener(
                        sessions,
                        config.joinVerification()::restricts,
                        ownTeleports,
                        player -> menus.menuIdOf(player.getOpenInventory().getTopInventory())
                                .isPresent(),
                        kernel.messages(),
                        kernel.messageSink()),
                new PinKeypadWindowListener(menus, keypad),
                new PinEntryChatGuard(
                        id -> sessions.isPending(id) || keypad.isEntering(id), kernel.messages(), kernel.messageSink()),
                new ReauthCommandListener(
                        opProtection.enabled(), opProtection.policy(), reauthState, reauthController, clock),
                new SecurityGuardListener(clientGuard, brands));
        return new Wired(
                commands,
                listeners,
                pending,
                sessions,
                limiter,
                keypad,
                reauthSessions,
                enrolmentSessions,
                reauthState,
                brands,
                ownTeleports,
                holdingArea,
                handoff,
                repository,
                forceReverification,
                clock);
    }

    /**
     * The network this backend is on, as far as the security module needs it.
     *
     * @param enabled whether this backend is on a network ({@code network.enabled})
     * @param bus the cross-server bus, where the proxy plugin's word about each arrival comes in
     * @param presence which backend each player is on, and which backends run
     * @param proxyAnswering whether the uxmEssentials proxy plugin answers on the bus now
     */
    public record Network(boolean enabled, Bus bus, NetworkPresence presence, BooleanSupplier proxyAnswering) {

        public Network {
            Objects.requireNonNull(bus, "bus");
            Objects.requireNonNull(presence, "presence");
            Objects.requireNonNull(proxyAnswering, "proxyAnswering");
        }
    }

    /**
     * Everything the security module contributes once wired: the {@code /2fa}, {@code /pin} and {@code /security}
     * command registrations and the join-verification listeners to publish, plus the transient registries cleared on stop (the pending
     * un-confirmed enrolments, the freeze/lockout sessions) and the keypad view whose open windows close on stop, so
     * a disable or reload leaves no residual secret and no locked player.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/freeze/keypad/op-protection Bukkit listeners to register
     * @param pending the pending un-confirmed TOTP enrolments, cleared on module stop
     * @param sessions the freeze registry, cleared on module stop
     * @param limiter the shared durable brute-force limiter, whose memory of lockouts is dropped on module stop
     * @param keypad the keypad view whose open windows close on module stop
     * @param reauthSessions the in-flight op-command re-auth prompts, cleared on module stop
     * @param enrolmentSessions the in-flight create-a-PIN entries, cleared on module stop
     * @param reauthState the per-player last-verified stamps, cleared on module stop
     * @param brands the session-only client-brand registry, cleared on module stop
     * @param ownTeleports the module's outstanding teleport exemptions, dropped on module stop
     * @param holdingArea the remembered pre-freeze origins, dropped on module stop
     * @param handoff the login-plugin hook, unregistered on module stop
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PendingTotpEnrollments pending,
            VerificationSessions sessions,
            AttemptLimiter limiter,
            PinKeypadView keypad,
            ReauthSessions reauthSessions,
            PinEnrolmentSessions enrolmentSessions,
            ReauthState reauthState,
            ClientBrandRegistry brands,
            FreezeTeleports ownTeleports,
            FreezeHoldingArea holdingArea,
            LoginPluginHandoff handoff,
            TwoFactorRepository repository,
            ForceReverification forceReverification,
            Clock clock) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(limiter, "limiter");
            Objects.requireNonNull(keypad, "keypad");
            Objects.requireNonNull(reauthSessions, "reauthSessions");
            Objects.requireNonNull(enrolmentSessions, "enrolmentSessions");
            Objects.requireNonNull(reauthState, "reauthState");
            Objects.requireNonNull(brands, "brands");
            Objects.requireNonNull(ownTeleports, "ownTeleports");
            Objects.requireNonNull(holdingArea, "holdingArea");
            Objects.requireNonNull(handoff, "handoff");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(forceReverification, "forceReverification");
            Objects.requireNonNull(clock, "clock");
        }

        /** Drop every pending secret, freeze, lockout and re-auth window, close every keypad, and forget every brand. */
        public void stop() {
            pending.clearAll();
            keypad.closeAll();
            sessions.clearAll();
            limiter.forgetKnown();
            reauthSessions.clearAll();
            enrolmentSessions.clearAll();
            reauthState.clearAll();
            brands.clearAll();
            ownTeleports.clearAll();
            holdingArea.clearAll();
            // The hook lives on another plugin's handler list, so a disable that left it there would keep calling into
            // a module that is no longer running.
            handoff.unhook();
        }
    }

    /** How often the release at start is tried before it is left to the log. */
    private static final int START_RELEASE_TRIES = 4;

    /**
     * Release the sessions this backend held from before {@code started}, trying again a little later each time the
     * database refuses, since a session it still seems to hold stays carryable to the player's address.
     */
    private static void releaseAtStart(
            KernelPorts kernel, SessionCarry carry, String serverId, java.time.Instant started, int attempt) {
        try {
            carry.started(serverId, started);
        } catch (RuntimeException failure) {
            if (attempt >= START_RELEASE_TRIES) {
                kernel.log().error("event=security_session_start_failed server=" + serverId, failure);
                return;
            }
            kernel.scheduler()
                    .asyncAfter(
                            java.time.Duration.ofSeconds(5L << (attempt - 1)),
                            () -> releaseAtStart(kernel, carry, serverId, started, attempt + 1));
        }
    }
}
