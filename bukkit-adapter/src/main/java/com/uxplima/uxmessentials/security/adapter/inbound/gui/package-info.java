/**
 * The security context's join-verification / op-command re-auth keypad, rendered through the menu engine: the
 * phone-pad chest a frozen player taps their PIN or authenticator code into. The {@code PinKeypadView} registers the
 * per-button {@code security:pin-*} actions, the masked-display / digit-label placeholders and the spec, and owns the
 * entered-PIN buffer (carried on the open menu as its subject) and the verify handoff; the engine's blanket click and
 * drag cancel is the pad's lock, so no item can move. The {@code PinKeypadCloseListener} reopens the window when a
 * still-frozen player escapes it, and both delegate the verify/lockout/trust decision to the controller behind
 * {@code KeypadActions}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.security.adapter.inbound.gui;
