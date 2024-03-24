package com.napier.singletons;

public class TickerTrackerSingleton {
    private static TickerTrackerSingleton instance;
    private int currentSimulationRun;
    private int currentDay;

    public static TickerTrackerSingleton getInstance() {
        if (instance == null) {
            instance = new TickerTrackerSingleton();
        }

        return instance;
    }

    public TickerTrackerSingleton() {
        this.currentSimulationRun = 0;
        this.currentDay = 0;
    }

    public int getCurrentSimulationRun() {
        return currentSimulationRun;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public void resetDayTracking() {
        this.currentDay = 0;
    }

    public void resetTracking() {
        this.currentSimulationRun = 0;
        this.currentDay = 0;
    }

    public void incrementCurrentSimulationRun() {
        this.currentSimulationRun++;
    }

    public void incrementCurrentDay() {
        this.currentDay++;
    }
}
