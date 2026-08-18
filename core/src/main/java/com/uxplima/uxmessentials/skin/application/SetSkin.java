package com.uxplima.uxmessentials.skin.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinPolicy;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import com.uxplima.uxmessentials.skin.domain.event.SkinChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Puts a chosen skin on a player: every {@code /skin} branch that changes what somebody wears ends here.
 *
 * <p>The refusals are decided before the network is touched, in the order an operator would expect: a source they
 * turned off, a name they blocked, a url from a host they did not list, a skin the player has no permission for,
 * then the cooldown. Only after all five does the texture get resolved, so a refused change costs nothing.
 *
 * <p>The cooldown is a player's own rate limit, so a staff member dressing somebody else is neither gated by it
 * nor charged it. Every call is blocking (a name lookup or an upload), so the caller reaches this off a tick
 * thread through the {@code Scheduler} port.
 */
@NullMarked
public final class SetSkin {

    /** One tier space, one stamp, keyed by the {@code skin} feature segment. */
    private static final String FEATURE = "skin";

    private final SkinRepository repository;
    private final SkinTextures textures;
    private final SkinUploads uploads;
    private final SkinView view;
    private final Permissions permissions;
    private final Cooldowns cooldowns;
    private final DomainEventPublisher events;
    private final SkinConfig config;
    private final Clock clock;

    public SetSkin(
            SkinRepository repository,
            SkinTextures textures,
            SkinUploads uploads,
            SkinView view,
            Permissions permissions,
            Cooldowns cooldowns,
            DomainEventPublisher events,
            SkinConfig config,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.textures = Objects.requireNonNull(textures, "textures");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.view = Objects.requireNonNull(view, "view");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.events = Objects.requireNonNull(events, "events");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Dress {@code target} in the skin {@code source} names, on {@code actor}'s behalf.
     *
     * @param actor who asked; the same player as {@code target} for a self-service change
     * @param target who ends up wearing it
     * @param source where to resolve the skin from
     * @param model the player model an uploaded image was cut for; ignored for a source that carries its own
     */
    public Outcome set(PlayerRef actor, PlayerRef target, SkinSource source, SkinModel model) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(model, "model");
        Outcome refusal = refuse(actor, target, source);
        if (refusal != Outcome.APPLIED) {
            return refusal;
        }
        Optional<SkinTexture> resolved = resolve(source, model);
        if (resolved.isEmpty()) {
            return source instanceof SkinSource.ByName ? Outcome.NOT_FOUND : Outcome.LOOKUP_FAILED;
        }
        apply(actor, target, source, model, resolved.get());
        return Outcome.APPLIED;
    }

    /** The first rule this change breaks, or {@link Outcome#APPLIED} when it breaks none. */
    private Outcome refuse(PlayerRef actor, PlayerRef target, SkinSource source) {
        if (!sourceEnabled(source)) {
            return Outcome.DISABLED_SOURCE;
        }
        SkinPolicy policy = config.policy();
        if (policy.blocked(source.value())) {
            return Outcome.BLOCKED;
        }
        if (source instanceof SkinSource.ByUrl url && !policy.urlAllowed(url.url())) {
            return Outcome.URL_NOT_ALLOWED;
        }
        if (source instanceof SkinSource.ByName name
                && !permissions.has(actor, policy.permissionFor(name.username()))) {
            return Outcome.NO_PERMISSION;
        }
        if (actor.equals(target) && cooldowns.check(actor, kind()).isErr()) {
            return Outcome.ON_COOLDOWN;
        }
        return Outcome.APPLIED;
    }

    /** Whether the operator left this source switched on. */
    private boolean sourceEnabled(SkinSource source) {
        return switch (source) {
            case SkinSource.ByName ignored -> config.nameSource();
            case SkinSource.ByUrl ignored -> config.urlSource();
            case SkinSource.ByFile ignored -> config.fileSource();
            case SkinSource.Bedrock ignored -> config.bedrockSource();
            case SkinSource.Fallback ignored -> true;
        };
    }

    /** The texture behind {@code source}, or empty when nothing could be resolved. */
    private Optional<SkinTexture> resolve(SkinSource source, SkinModel model) {
        return switch (source) {
            case SkinSource.ByName name -> textures.fetchNow(name.username());
            case SkinSource.ByUrl url -> uploads.fromUrl(url.url(), model);
            case SkinSource.ByFile file -> uploads.fromFile(file.fileName(), model);
            case SkinSource.Bedrock ignored -> Optional.empty();
            case SkinSource.Fallback fallback -> textures.fetchNow(fallback.name());
        };
    }

    /** Store the choice, put it on the player, start their clock, and say so. */
    private void apply(PlayerRef actor, PlayerRef target, SkinSource source, SkinModel model, SkinTexture texture) {
        PlayerSkin skin = new PlayerSkin(target, source, texture, model, clock.instant());
        repository.save(skin);
        view.apply(target, texture, model);
        if (actor.equals(target)) {
            cooldowns.stamp(actor, kind());
        }
        events.publish(new SkinChanged(target, source, skin.appliedAt()));
    }

    private CooldownKind kind() {
        return new CooldownKind(FEATURE, config.cooldown().toSeconds(), CooldownStartPhase.TELEPORT);
    }

    /** What became of a set. */
    public enum Outcome {
        /** The player is wearing the new skin. */
        APPLIED,
        /** The operator turned this source off. */
        DISABLED_SOURCE,
        /** The operator blocked this skin. */
        BLOCKED,
        /** The url points at a host the operator did not allow. */
        URL_NOT_ALLOWED,
        /** The player does not hold this skin's own permission node. */
        NO_PERMISSION,
        /** The player has changed their skin too recently. */
        ON_COOLDOWN,
        /** No account by that name has a skin. */
        NOT_FOUND,
        /** The image could not be turned into a signed texture. */
        LOOKUP_FAILED
    }
}
