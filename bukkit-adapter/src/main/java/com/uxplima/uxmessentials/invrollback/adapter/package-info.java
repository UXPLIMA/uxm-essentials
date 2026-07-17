/**
 * The invrollback context's Bukkit adapter: {@link com.uxplima.uxmessentials.invrollback.adapter.InvrollbackWiring}
 * constructs the jOOQ snapshot repository, the {@code CaptureSnapshot} use case, and the death/logout capture
 * listener over the injected kernel ports and the persistence DSL. The listener and the item-serialization codec
 * are the anti-corruption layer between the live Bukkit inventory and the domain's opaque snapshot bytes.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.invrollback.adapter;
