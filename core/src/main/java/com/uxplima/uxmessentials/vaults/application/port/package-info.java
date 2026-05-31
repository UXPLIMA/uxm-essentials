/**
 * The vaults context's outbound ports: {@code VaultRepository} for durable, per-owner vault storage (the
 * queryable owner/index/size/last-touched columns plus the opaque serialized contents) and {@code VaultAudit}
 * for the admin-open audit trail. The application depends only on these interfaces; the jOOQ repository and the
 * audit logger implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.application.port;
