package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * {@link KitsPlaceholders} over the kits context's {@link KitRepository} and the shared {@link Cooldowns}
 * gate. The cooldown kind is rebuilt exactly as the {@code KitAccess} claim path keys it — the shared {@code
 * kit} tier node with a per-id stamp scope — so the remaining time a placeholder reports matches what a
 * {@code /kit} claim would enforce.
 *
 * <p>An id that no kit carries yields {@link Optional#empty()}; a malformed id (rejected by {@link
 * KitId#of}) is treated the same way rather than propagating the validation error onto the placeholder
 * surface. A ready kit returns {@link Duration#ZERO}.
 */
@NullMarked
public final class KitCooldownPlaceholders implements KitsPlaceholders {

    private final KitRepository repository;
    private final Cooldowns cooldowns;

    public KitCooldownPlaceholders(KitRepository repository, Cooldowns cooldowns) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
    }

    @Override
    public Optional<Duration> cooldownRemaining(PlayerRef who, String kitId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kitId, "kitId");
        Optional<KitDefinition> kit = lookup(kitId);
        if (kit.isEmpty()) {
            return Optional.empty();
        }
        Result<Unit, Duration> gate = cooldowns.check(who, cooldownKind(kit.get()));
        return Optional.of(gate.isErr() ? gate.errorOrThrow() : Duration.ZERO);
    }

    private Optional<KitDefinition> lookup(String kitId) {
        try {
            return repository.find(KitId.of(kitId));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static CooldownKind cooldownKind(KitDefinition kit) {
        return CooldownKind.scoped(
                "kit", "kit." + kit.id().value(), kit.cooldownSeconds(), CooldownStartPhase.TELEPORT);
    }
}
