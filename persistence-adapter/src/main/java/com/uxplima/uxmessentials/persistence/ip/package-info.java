/**
 * The kernel IP-history adapter: the jOOQ {@code IpHistoryStore} over the {@code ip_history} table, which holds
 * the one record of which accounts have connected from which addresses. Associations are keyed by the opaque
 * token the caller tokenises the address into, and the raw address column is filled in only while the moderation
 * module retains it. Both alt lookups ({@code /alts}, {@code /ipalts}) and the join-time account cap read from
 * here, so no context keeps a second copy.
 */
@NullMarked
package com.uxplima.uxmessentials.persistence.ip;

import org.jspecify.annotations.NullMarked;
