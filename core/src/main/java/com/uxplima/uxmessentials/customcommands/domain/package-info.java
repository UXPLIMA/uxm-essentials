/**
 * Pure domain of the customcommands bounded context: what an operator-defined command <em>is</em>. A
 * {@code CustomCommand} carries its identity, the literal it registers under, the arguments it accepts, the gates
 * it runs through and the chain of action tokens it fires. The action and requirement tokens are opaque here: the
 * domain validates their shape and their ordering, never their meaning, which belongs to the adapter that hands
 * them to the shared menu vocabulary. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.customcommands.domain;
