package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.permission.EconomyPermissions;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmlib.command.Gate;
import org.junit.jupiter.api.Test;

class CommandSpecCheckTest {

    @Test
    void aCommandRegisteredAsTheCatalogueDescribesItRaisesNothing() {
        CommandRegistration balance = new Registration(
                "balance", "balance", List.of("bal", "money"), Gate.permission(EconomyPermissions.ECONOMY_BALANCE));

        assertThat(CommandSpecCheck.problems(List.of(balance))).isEmpty();
    }

    @Test
    void aCommandTheCatalogueDoesNotListIsNamed() {
        CommandRegistration stray = new Registration("stray", "stray", List.of(), Gate.permission("uxmessentials.x"));

        assertThat(CommandSpecCheck.problems(List.of(stray)))
                .singleElement()
                .asString()
                .contains("/stray", "does not list it");
    }

    @Test
    void aCommandGatedByAnotherNodeThanTheCatalogueSaysIsNamedWithBothNodes() {
        CommandRegistration balance =
                new Registration("balance", "balance", List.of("bal"), Gate.permission(EconomyPermissions.ECONOMY_PAY));

        assertThat(CommandSpecCheck.problems(List.of(balance)))
                .singleElement()
                .asString()
                .contains(EconomyPermissions.ECONOMY_PAY, EconomyPermissions.ECONOMY_BALANCE);
    }

    @Test
    void anAliasTheCatalogueDoesNotPublishIsNamed() {
        CommandRegistration balance = new Registration(
                "balance", "balance", List.of("cash"), Gate.permission(EconomyPermissions.ECONOMY_BALANCE));

        assertThat(CommandSpecCheck.problems(List.of(balance)))
                .singleElement()
                .asString()
                .contains("cash");
    }

    @Test
    void aCommandUnderAnotherNameThanItsIdIsComparedByItsName() {
        CommandRegistration twoFactor = new Registration(
                "twofactor", "2fa", List.of("twofactor"), Gate.permission(EnforcementPermissions.SECURITY_2FA));

        assertThat(CommandSpecCheck.problems(List.of(twoFactor))).isEmpty();
    }

    @Test
    void theNodeIsReadFromTheFirstGateThatRefuses() {
        CommandRegistration balance = new Registration(
                "balance",
                "balance",
                List.of(),
                Gate.permission(EconomyPermissions.ECONOMY_BALANCE)
                        .or(Gate.permission(EconomyPermissions.ECONOMY_PAY)));

        assertThat(CommandSpecCheck.gatedBy(balance)).contains(EconomyPermissions.ECONOMY_BALANCE);
    }

    @Test
    void aCommandBuiltFromAnOperatorsFileIsNotCompared() {
        CommandRegistration page = new Registration("rulesextra", "rulesextra", List.of(), Gate.open()) {
            @Override
            public boolean declared() {
                return false;
            }
        };

        assertThat(CommandSpecCheck.problems(List.of(page))).isEmpty();
    }

    private static class Registration implements CommandRegistration {

        private final String id;
        private final String name;
        private final List<String> aliases;
        private final Gate gate;

        Registration(String id, String name, List<String> aliases, Gate gate) {
            this.id = id;
            this.name = name;
            this.aliases = aliases;
            this.gate = gate;
        }

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                    .requires(gate)
                    .build();
        }

        @Override
        public String description() {
            return "A command under test.";
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public String commandId() {
            return id;
        }

        @Override
        public String defaultName() {
            return name;
        }
    }
}
