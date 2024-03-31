package com.napier.singletons;

/**
 * Contains the state of the currently running simulation set.
 *
 * @author László Tárkányi
 */
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

    /**
     * Assigns the initial value to the tracking variables.
     * (They both start at 0.)
     */
    public TickerTrackerSingleton() {
        this.currentSimulationRun = 0;
        this.currentDay = 0;
    }

    /* Accessors */

    public int getCurrentSimulationRun() {
        return currentSimulationRun;
    }

    public int getCurrentDay() {
        return currentDay;
    }

    /* Mutators */

    /**
     * Resets the day tracking variable to its initial value (0).
     * Used at the start of a simulation run.
     */
    public void resetDayTracking() {
        this.currentDay = 0;
    }

    /**
     * Resets all tracking variables to their initial values (0).
     * Used at the start of a simulation set.
     */
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
