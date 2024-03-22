package com.napier;

import jade.core.AID;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.Objects;

public class TradeOffer implements Serializable {
    private final PropertyChangeSupport propertyChangeSupport;
    private final AID requesterAgent;
    private final AID receiverAgent;
    private final TimeSlot timeSlotOffered;
    private final TimeSlot timeSlotRequested;
    private boolean isAccepted;

    public TradeOffer(AID requesterAgent, AID receiverAgent, TimeSlot timeSlotOffered, TimeSlot timeSlotRequested) {
        this.propertyChangeSupport = new PropertyChangeSupport(this);
        this.requesterAgent = requesterAgent;
        this.receiverAgent = receiverAgent;
        this.timeSlotOffered = timeSlotOffered;
        this.timeSlotRequested = timeSlotRequested;
        this.isAccepted = false;
    }

    public AID getRequesterAgent() {
        return requesterAgent;
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

    public void acceptTrade() {
        propertyChangeSupport.firePropertyChange("Trade Accepted", this.isAccepted, true);
        this.isAccepted = true;
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        this.propertyChangeSupport.addPropertyChangeListener(propertyChangeListener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        this.propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        TradeOffer that = (TradeOffer) object;

        return Objects.equals(this.requesterAgent, that.requesterAgent)
                && Objects.equals(this.receiverAgent, that.receiverAgent)
                && Objects.equals(this.timeSlotOffered, that.timeSlotOffered)
                && Objects.equals(this.timeSlotRequested, that.timeSlotRequested);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requesterAgent, receiverAgent, timeSlotOffered, timeSlotRequested);
    }
}
