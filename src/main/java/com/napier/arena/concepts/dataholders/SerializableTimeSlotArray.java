package com.napier.arena.concepts.dataholders;

import com.napier.arena.concepts.TimeSlot;

import java.io.Serializable;

/**
 * A wrapper for an array of timeslot objects.
 *
 * @author László Tárkányi
 *
 * @param timeSlots (TimeSlot[]) The array containing the timeslot objects.
 */
public record SerializableTimeSlotArray(
        TimeSlot[] timeSlots
) implements Serializable {
    // no-op
}