package com.napier;

import java.io.Serializable;
import java.util.ArrayList;

public record InitialTimeSlotAllocation(TimeSlot[] timeSlots) implements Serializable {
    // no-op
}
