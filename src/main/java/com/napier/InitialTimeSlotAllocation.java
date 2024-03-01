package com.napier;

import java.io.Serializable;

public record InitialTimeSlotAllocation(TimeSlot[] timeSlots) implements Serializable {
    // no-op
}
