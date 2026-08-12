package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.ServerTweaksPlaceholders;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ServerTweaksPlaceholders} seam over the module's own config snapshot: the brand it reports, or nothing
 * when the tweak is switched off and the server's stock brand stands. The snapshot is the same immutable record the
 * join listener sends from, so the placeholder and the F3 screen cannot disagree.
 */
@NullMarked
public final class ConfigServerTweaksPlaceholders implements ServerTweaksPlaceholders {

    private final ServerTweaksConfig.F3Brand brand;

    public ConfigServerTweaksPlaceholders(ServerTweaksConfig.F3Brand brand) {
        this.brand = Objects.requireNonNull(brand, "brand");
    }

    @Override
    public Optional<String> brand() {
        return brand.enabled() ? Optional.of(brand.brand()) : Optional.empty();
    }
}
