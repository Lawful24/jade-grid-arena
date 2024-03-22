package com.napier;

import jade.core.AID;

import java.io.Serializable;
import java.util.Objects;

public record TradeOffer(
        AID senderAgent,
        AID receiverAgent,
        TimeSlot timeSlotOffered,
        TimeSlot timeSlotRequested
) implements Serializable {
    public void acceptTrade() {
        // TODO: notify the smart contract
    }
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        TradeOffer that = (TradeOffer) object;

        return Objects.equals(this.senderAgent, that.senderAgent)
                && Objects.equals(this.receiverAgent, that.receiverAgent)
                && Objects.equals(this.timeSlotOffered, that.timeSlotOffered)
                && Objects.equals(this.timeSlotRequested, that.timeSlotRequested);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderAgent, receiverAgent, timeSlotOffered, timeSlotRequested);
    }
}
