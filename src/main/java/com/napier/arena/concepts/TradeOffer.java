package com.napier.arena.concepts;

import jade.core.AID;

import java.io.Serializable;

/**
 * A wrapper for an exchange of timeslots between two Household agents.
 *
 * @author László Tárkányi
 *
 * @param requesterAgent The Household agent that requested the exchange.
 * @param receiverAgent The Household agent that received a request for the exchange.
 * @param timeSlotOffered The timeslot provided by the agent that requested the exchange.
 * @param timeSlotRequested The timeslot desired by the agent that requested the exchange.
 */
public record TradeOffer (
        AID requesterAgent,
        AID receiverAgent,
        TimeSlot timeSlotOffered,
        TimeSlot timeSlotRequested
) implements Serializable {
    // no-op
}