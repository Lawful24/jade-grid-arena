package com.napier;

import jade.core.AID;

import java.io.Serializable;

public record TradeOffer(AID senderAgent, TimeSlot timeSlotOffered, TimeSlot timeSlotRequested) implements Serializable {
    // no-op
}
