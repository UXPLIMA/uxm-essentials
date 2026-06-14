package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins the unified review surface into the moderation context: {@code /history} and {@code /staffhistory} carry
 * their own nodes (the unified record can be granted apart from the act-on-sanction nodes), and {@code /checkban}
 * and {@code /checkmute} share one {@code check} node. This guard fails if any literal drops out of the surface
 * or wires under the wrong node.
 */
class UnifiedHistorySurfaceDriftTest {

    private static CommandSpec moderationSpec(String literal) {
        FeatureModule moderation = new DefaultModuleRegistry()
                .byId(ModuleId.of("moderation"))
                .orElseThrow(() -> new AssertionError("moderation module must be registered"));
        return moderation.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("moderation surface must expose a /" + literal + " command"));
    }

    @Test
    void historyWiresUnderItsOwnNode() {
        assertThat(moderationSpec("history").permission()).isEqualTo("uxmessentials.moderation.history");
    }

    @Test
    void staffHistoryWiresUnderItsOwnNode() {
        assertThat(moderationSpec("staffhistory").permission()).isEqualTo("uxmessentials.moderation.staffhistory");
    }

    @Test
    void staffRollbackWiresUnderItsOwnNode() {
        assertThat(moderationSpec("staffrollback").permission()).isEqualTo("uxmessentials.moderation.staffrollback");
    }

    @Test
    void checkBanWiresUnderTheCheckNode() {
        assertThat(moderationSpec("checkban").permission()).isEqualTo("uxmessentials.moderation.check");
    }

    @Test
    void checkMuteSharesTheCheckNodeWithCheckBan() {
        assertThat(moderationSpec("checkmute").permission()).isEqualTo("uxmessentials.moderation.check");
    }
}
