/**
 * The invrollback context's outbound persistence adapter: the jOOQ {@link
 * com.uxplima.uxmessentials.persistence.invrollback.JooqSnapshotRepository} over the generated V79 {@code
 * inv_snapshots} table, and the {@link com.uxplima.uxmessentials.persistence.invrollback.SnapshotRows}
 * anti-corruption mapping (the queryable id/owner/cause/created-at columns plus the base64-encoded opaque
 * inventory payload). The bukkit-adapter wires the repository over {@code persistence.dsl()} without naming a
 * jOOQ type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.invrollback;
