package com.napier;

import jade.core.AID;

import java.io.Serializable;
import java.util.Objects;

public class TradeOffer implements Serializable {
    private AID senderAgent;
    private AID receiverAgent;
    private TimeSlot timeSlotOffered;
    private TimeSlot timeSlotRequested;

    public TradeOffer(AID senderAgent, AID receiverAgent, TimeSlot timeSlotOffered, TimeSlot timeSlotRequested) {
        this.senderAgent = senderAgent;
        this.receiverAgent = receiverAgent;
        this.timeSlotOffered = timeSlotOffered;
        this.timeSlotRequested = timeSlotRequested;
    }

    public AID getSenderAgent() {
        return senderAgent;
    }

    public AID getReceiverAgent() {
        return receiverAgent;
    }

    public TimeSlot getTimeSlotOffered() {
        return timeSlotOffered;
    }

    public TimeSlot getTimeSlotRequested() {
        return timeSlotRequested;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        TradeOffer that = (TradeOffer) object;
        return Objects.equals(senderAgent, that.senderAgent)
                && Objects.equals(receiverAgent, that.receiverAgent)
                && Objects.equals(timeSlotOffered, that.timeSlotOffered)
                && Objects.equals(timeSlotRequested, that.timeSlotRequested);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderAgent, receiverAgent, timeSlotOffered, timeSlotRequested);
    }
}
