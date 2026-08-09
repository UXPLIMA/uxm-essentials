package com.uxplima.uxmessentials.shared.domain;

/**
 * An action that is about to happen and that something outside the plugin may refuse.
 *
 * <p>The mirror image of {@link DomainEvent}. A domain event is a fact: it has happened, it is recorded, and a
 * listener can only react to it. A proposal is the question asked just before the fact: it carries the same values
 * the resulting event will, but nothing has been written yet, so an answer of "no" still means something.
 *
 * <p>A use case asks by handing one to the {@code DomainGate} port. What answers is the adapter's business; from the
 * domain's side a proposal is a value, and refusal is a modelled outcome rather than an exception.
 *
 * <p>Concrete proposals are records, one per vetoable action, grouped into a sealed per-context family the way
 * domain events are. The name is the action in the present progressive, {@code HomeCreating} to
 * {@code HomeCreated}, so a proposal and the fact it precedes read as the two halves of one operation.
 */
public interface DomainProposal {}
