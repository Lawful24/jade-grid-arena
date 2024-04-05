package com.napier.arena.concepts;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an immutable timeslot asset.
 *
 * @author László Tárkányi
 */
public class TimeSlot implements Serializable {
    private final int startHour;
    private final double energyAvailableKwh = 1.0;

    public TimeSlot(int startHour) {
        this.startHour = startHour;
    }

    public int getStartHour() {
        return startHour;
    }

    @Override
    public String toString() {
        return Integer.toString(this.startHour);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;

        if (object == null || getClass() != object.getClass()) return false;

        TimeSlot that = (TimeSlot) object;

        return this.startHour == that.startHour;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startHour);
    }
}