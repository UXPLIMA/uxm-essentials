package com.uxplima.uxmessentials.economy.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /payall} into the economy context's command surface. Unlike the admin {@code /eco giveall} (free
 * credit), {@code /payall} debits the sender's own wallet and pays every online recipient through the existing
 * {@code Pay} use case; operators expect it for events and giveaways. This guard fails if the literal drops out
 * of the surface or wires under a node other than {@code uxmessentials.economy.payall}.
 */
class PayAllSurfaceDriftTest {

    private static CommandSpec economySpec(String literal) {
        FeatureModule economy = new DefaultModuleRegistry()
                .byId(ModuleId.of("economy"))
                .orElseThrow(() -> new AssertionError("economy module must be registered"));
        return economy.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("economy surface must expose a /" + literal + " command"));
    }

    @Test
    void economySurfaceExposesPayAll() {
        assertThat(economySpec("payall").literal()).isEqualTo("payall");
    }

    @Test
    void payAllWiresUnderItsOwnPermission() {
        assertThat(economySpec("payall").permission()).isEqualTo("uxmessentials.economy.payall");
    }
}
