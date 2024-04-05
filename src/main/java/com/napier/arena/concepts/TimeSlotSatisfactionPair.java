package com.napier.arena.concepts;

/**
 * @see <a href="https://github.com/NathanABrooks/ResourceExchangeArena/blob/master/src/resource_exchange_arena/SlotSatisfactionPair.java">ResourceExchangeArena</a>
 */
public class TimeSlotSatisfactionPair {
    TimeSlot timeSlot;
    double satisfaction;

    /**
     * Used to associate a time-slot  with its satisfaction level for an agent.
     *
     * @param timeSlot Custom object representing the specific time-slot.
     * @param satisfaction Double value representing the level of satisfaction.
     */
    public TimeSlotSatisfactionPair(TimeSlot timeSlot, double satisfaction) {
        this.timeSlot = timeSlot;
        this.satisfaction = satisfaction;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public double getSatisfaction() {
        return satisfaction;
    }

    @Override
    public String toString() {
        return "(" + this.timeSlot + ", " + this.satisfaction + ")";
    }
}