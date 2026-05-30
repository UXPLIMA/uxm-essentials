package com.uxplima.uxmessentials.persistence.economy;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed native-ledger persistence handle the economy adapter wiring holds: the (cached) jOOQ
 * {@link WalletRepository}, the debounced settle writer, and the append-only transaction telemetry. The
 * three are built together by {@link WalletRepositories} from the shared {@code Persistence} DSL and the
 * kernel {@code Scheduler}/{@code Logger}, so the economy context wires its native provider over the
 * repository while the writer and telemetry loops are armed on start and drained on stop.
 *
 * <p>The repository is the seam the {@code NativeEconomyProvider} reads and writes through; the writer and
 * telemetry are the persistence-side optimisations below the port that no consumer sees ({@code
 * docs/11-economy-integration.md} §10).
 *
 * @param repository the cached jOOQ wallet repository the native provider binds to
 * @param writer the debounced/coalesced settle queue (idempotent last-value-wins path only)
 * @param telemetry the batched, append-only transaction-history writer
 */
@NullMarked
public record WalletLedger(WalletRepository repository, DebouncedWalletWriter writer, TransactionTelemetry telemetry) {

    public WalletLedger {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(telemetry, "telemetry");
    }

    /** Arm the debounced settle loop and the telemetry batch loop. Called once on economy module start. */
    public void start() {
        writer.start();
        telemetry.start();
    }

    /** Drain both queues synchronously and stop their loops. Called on economy module stop and reload swap. */
    public void stop() {
        writer.stop();
        telemetry.stop();
    }
}
