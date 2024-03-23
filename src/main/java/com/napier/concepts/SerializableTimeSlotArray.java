package com.napier.concepts;

import java.io.Serializable;

public record SerializableTimeSlotArray(TimeSlot[] timeSlots) implements Serializable {
    // no-op
}
