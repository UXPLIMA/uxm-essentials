/**
 * The kits context's bukkit-side wiring: {@code KitsWiring} constructs the use cases over the kernel ports,
 * the per-kit-file repository under {@code modules/kits/kits/}, the PDC claim store, and the inventory
 * granter, and produces the
 * Brigadier command list the plugin registers. {@code KitServices} holds the constructed use cases the
 * commands share. The per-kit cost soft-couples to the economy context through the optional {@code KitEconomy}
 * bridge captured during economy wiring.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.adapter;
