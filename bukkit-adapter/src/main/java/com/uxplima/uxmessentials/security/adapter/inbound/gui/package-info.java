/**
 * The security context's join-verification keypad GUI: a bespoke numeric-pad inventory (a sanctioned raw-inventory
 * leaf, not a spec menu, because it captures live per-keystroke clicks and force-reopens on an escape) that a frozen
 * player taps their PIN or authenticator code into. The {@code PinKeypadView} builds and tracks the window, the
 * {@code PinKeypadListener} routes clicks and reopens an escaped window, and both delegate the verify/lockout/trust
 * decision to the controller behind {@code KeypadActions}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.security.adapter.inbound.gui;
