package com.uxplima.uxmessentials.security.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The security context's sealed family of domain events.
 *
 * <p>What each carries is deliberately thin: who, and how it went. Nothing here names which factor was presented or
 * how close a wrong value was, because the verification itself does not learn either, and an event that leaked it
 * would undo the constant-time comparison it came from.
 */
public sealed interface SecurityEvent extends DomainEvent
        permits VerificationPassed, VerificationFailed, AccountLockedOut {}
