package com.napier;

import java.io.Serializable;

public record SerializableTimeSlotArray(TimeSlot[] timeSlots) implements Serializable {
    // no-op
}
