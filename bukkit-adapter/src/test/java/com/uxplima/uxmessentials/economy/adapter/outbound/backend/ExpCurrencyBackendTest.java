package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The experience backend against MockBukkit online players. It is the one shipped backend that cannot be written
 * offline, its debit is guarded against overdraw, and a credit followed by a balance read agrees with the vanilla
 * experience curve — the round trip {@code applyTotal}/{@code readTotal} promises.
 */
class ExpCurrencyBackendTest {

    private static final Currency XP =
            Currency.builder(CurrencyId.of("xp")).precision(0).build();

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOfflineOwnerCannotBeCredited() {
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server);
        PlayerRef ghost = new PlayerRef(UUID.randomUUID(), "Ghost");

        assertThat(backend.worksOffline()).isFalse();
        assertThat(backend.credit(ghost, Money.of(XP, BigDecimal.TEN)).errorOrThrow())
                .isEqualTo(TransferError.PLAYER_OFFLINE);
    }

    @Test
    void debitingMoreThanTheOwnerHasIsRejectedWithoutMutating() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server);
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        backend.credit(ref, Money.of(XP, BigDecimal.valueOf(10)));

        assertThat(backend.debit(ref, Money.of(XP, BigDecimal.valueOf(11))).errorOrThrow())
                .isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aCreditThenBalanceRoundTripsThroughTheVanillaCurve() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        ExpCurrencyBackend backend = new ExpCurrencyBackend(server);
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());

        assertThat(backend.credit(ref, Money.of(XP, BigDecimal.valueOf(100))).isOk())
                .isTrue();
        assertThat(backend.balance(ref, XP).amount()).isEqualByComparingTo("100");
    }
}
