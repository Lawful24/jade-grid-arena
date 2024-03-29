package com.napier.concepts.dataholders;

import com.napier.concepts.TimeSlot;

import java.io.Serializable;

public record SerializableTimeSlotArray(
        TimeSlot[] timeSlots
) implements Serializable {
    // no-op
}
