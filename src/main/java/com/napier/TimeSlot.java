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
}
