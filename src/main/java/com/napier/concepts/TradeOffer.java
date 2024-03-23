package com.napier.concepts;

import jade.core.AID;

import java.io.Serializable;

public record TradeOffer (
        AID requesterAgent,
        AID receiverAgent,
        TimeSlot timeSlotOffered,
        TimeSlot timeSlotRequested
) implements Serializable {
    // no-op
}
