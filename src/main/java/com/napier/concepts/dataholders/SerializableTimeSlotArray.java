package com.napier.concepts.dataholders;

import com.napier.concepts.TimeSlot;

import java.io.Serializable;

/**
 * A wrapper for an array of timeslot objects.
 *
 * @author László Tárkányi
 *
 * @param timeSlots The array containing the timeslot objects.
 */
public record SerializableTimeSlotArray(
        TimeSlot[] timeSlots
) implements Serializable {
    // no-op
}