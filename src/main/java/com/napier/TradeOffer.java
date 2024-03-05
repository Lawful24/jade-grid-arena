package com.napier;

import jade.core.AID;

import java.io.Serializable;

public record TradeOffer(AID senderAgent, TimeSlot timeSlotToGive, TimeSlot timeSlotToReceive) implements Serializable {
    // no-op
}
