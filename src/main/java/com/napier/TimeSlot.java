package com.napier;

public class TimeSlot {
    private int startHour;
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
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        } else if (obj == this) {
            return true;
        } else {
            TimeSlot timeSlot = (TimeSlot) obj;

            return this.startHour == timeSlot.getStartHour();
        }
    }
}
