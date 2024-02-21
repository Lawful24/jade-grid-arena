package com.napier;

// TODO: Cite Arena code
public class SlotSatisfactionPair {
    TimeSlot timeSlot;
    double satisfaction;

    /**
     * Used to associate a time-slot  with its satisfaction level for an agent.
     *
     * @param timeSlot Custom object representing the specific time-slot.
     * @param satisfaction Double value representing the level of satisfaction.
     */
    public SlotSatisfactionPair(TimeSlot timeSlot, double satisfaction) {
        this.timeSlot = timeSlot;
        this.satisfaction = satisfaction;
    }
}
