package com.uxplima.uxmessentials.economy.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;

import com.uxplima.uxmessentials.economy.adapter.outbound.BukkitInventoryCalculations;
import com.uxplima.uxmessentials.economy.adapter.outbound.PhysicalWalletRepositoryDecorator;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PhysicalWalletRepositoryDecoratorTest {

    private ServerMock server;
    private WalletRepository delegate;
    private PendingTransactionRepository pendingRepo;
    private BukkitInventoryCalculations calculations;
    private CurrencyRegistry registry;
    private Currency gems;
    private Currency coins; // Virtual
    private PhysicalWalletRepositoryDecorator decorator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        delegate = mock(WalletRepository.class);
        pendingRepo = mock(PendingTransactionRepository.class);
        calculations = mock(BukkitInventoryCalculations.class);

        gems = mock(Currency.class);
        when(gems.id()).thenReturn(CurrencyId.of("gems"));
        when(gems.isPhysical()).thenReturn(true);
        when(gems.precision()).thenReturn(0);
        when(gems.normalize(any(BigDecimal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(coins.isPhysical()).thenReturn(false);
        when(coins.normalize(any(BigDecimal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registry = mock(CurrencyRegistry.class);
        when(registry.all()).thenReturn(Set.of(gems, coins));

        decorator = new PhysicalWalletRepositoryDecorator(
                delegate, pendingRepo, calculations, registry, new InlineScheduler(), new ServerPlayerLookup(), log());
    }

    private Logger log() {
        return mock(Logger.class);
    }

    /** A scheduler that runs marshalled inventory/roster work inline so the synchronous decorator can be tested. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Resolves online state from the MockBukkit server so the decorator's online/offline branches are exercised. */
    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            var bukkit = server.getPlayer(uuid);
            return bukkit != null && bukkit.isOnline();
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testFindByOwner_OnlinePlayer_LoadsFromInventory() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = new PlayerRef(player.getUniqueId(), "Alice");

        Wallet dbWallet = Wallet.of(ref, Map.of(coins, Money.of(coins, BigDecimal.valueOf(100))));
        when(delegate.findByOwner(eq(ref))).thenReturn(Optional.of(dbWallet));
        when(calculations.getBalance(eq(player), eq(gems))).thenReturn(BigDecimal.valueOf(25));

        Optional<Wallet> walletOpt = decorator.findByOwner(ref);
        assertThat(walletOpt).isPresent();

        Wallet wallet = walletOpt.get();
        // Virtual coin is still from DB
        assertThat(wallet.balanceOf(coins).amount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        // Physical gem is from inventory
        assertThat(wallet.balanceOf(gems).amount()).isEqualByComparingTo(BigDecimal.valueOf(25));
    }

    @Test
    void testFindByOwner_OfflinePlayer_ReturnsZeroForPhysical() {
        UUID offlineUuid = UUID.randomUUID();
        PlayerRef ref = new PlayerRef(offlineUuid, "Bob");

        Wallet dbWallet = Wallet.of(ref, Map.of(coins, Money.of(coins, BigDecimal.valueOf(100))));
        when(delegate.findByOwner(eq(ref))).thenReturn(Optional.of(dbWallet));

        Optional<Wallet> walletOpt = decorator.findByOwner(ref);
        assertThat(walletOpt).isPresent();

        Wallet wallet = walletOpt.get();
        assertThat(wallet.balanceOf(coins).amount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(wallet.balanceOf(gems).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testCredit_OnlinePlayer_CreditsInventory() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = new PlayerRef(player.getUniqueId(), "Alice");
        Money amount = Money.of(gems, BigDecimal.valueOf(50));

        when(calculations.credit(eq(player), eq(amount))).thenReturn(Result.ok());

        Result<Unit, TransferError> result = decorator.credit(ref, amount);
        assertThat(result.isOk()).isTrue();

        verify(calculations).credit(eq(player), eq(amount));
        verify(pendingRepo, never()).queueCredit(any(), any(), any());
    }

    @Test
    void testCredit_OfflinePlayer_QueuesCredit() {
        UUID offlineUuid = UUID.randomUUID();
        PlayerRef ref = new PlayerRef(offlineUuid, "Bob");
        Money amount = Money.of(gems, BigDecimal.valueOf(50));

        Result<Unit, TransferError> result = decorator.credit(ref, amount);
        assertThat(result.isOk()).isTrue();

        verify(pendingRepo).queueCredit(eq(offlineUuid), eq("gems"), eq(BigDecimal.valueOf(50)));
        verify(calculations, never()).credit(any(), any());
    }

    @Test
    void testDebit_OnlinePlayer_DebitsInventory() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = new PlayerRef(player.getUniqueId(), "Alice");
        Money amount = Money.of(gems, BigDecimal.valueOf(10));

        when(calculations.debit(eq(player), eq(amount))).thenReturn(Result.ok());

        Result<Unit, TransferError> result = decorator.debit(ref, amount);
        assertThat(result.isOk()).isTrue();

        verify(calculations).debit(eq(player), eq(amount));
    }

    @Test
    void testDebit_OfflinePlayer_ReturnsOfflineError() {
        UUID offlineUuid = UUID.randomUUID();
        PlayerRef ref = new PlayerRef(offlineUuid, "Bob");
        Money amount = Money.of(gems, BigDecimal.valueOf(10));

        Result<Unit, TransferError> result = decorator.debit(ref, amount);
        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.PLAYER_OFFLINE);
        verify(calculations, never()).debit(any(), any());
    }

    @Test
    void testTransfer_BothOnline_DebitsSenderAndCreditsReceiver() {
        PlayerMock sender = server.addPlayer("Alice");
        PlayerMock receiver = server.addPlayer("Bob");
        PlayerRef senderRef = new PlayerRef(sender.getUniqueId(), "Alice");
        PlayerRef receiverRef = new PlayerRef(receiver.getUniqueId(), "Bob");
        Money amount = Money.of(gems, BigDecimal.valueOf(15));

        when(calculations.debit(eq(sender), eq(amount))).thenReturn(Result.ok());
        when(calculations.credit(eq(receiver), eq(amount))).thenReturn(Result.ok());

        Result<Unit, TransferError> result = decorator.transfer(senderRef, receiverRef, amount);
        assertThat(result.isOk()).isTrue();

        verify(calculations).debit(eq(sender), eq(amount));
        verify(calculations).credit(eq(receiver), eq(amount));
        verify(pendingRepo, never()).queueCredit(any(), any(), any());
    }

    @Test
    void testTransfer_ReceiverOffline_DebitsSenderAndQueuesReceiver() {
        PlayerMock sender = server.addPlayer("Alice");
        UUID offlineUuid = UUID.randomUUID();
        PlayerRef senderRef = new PlayerRef(sender.getUniqueId(), "Alice");
        PlayerRef receiverRef = new PlayerRef(offlineUuid, "Bob");
        Money amount = Money.of(gems, BigDecimal.valueOf(15));

        when(calculations.debit(eq(sender), eq(amount))).thenReturn(Result.ok());

        Result<Unit, TransferError> result = decorator.transfer(senderRef, receiverRef, amount);
        assertThat(result.isOk()).isTrue();

        verify(calculations).debit(eq(sender), eq(amount));
        verify(pendingRepo).queueCredit(eq(offlineUuid), eq("gems"), eq(BigDecimal.valueOf(15)));
    }
}
