package com.napier.arena.concepts.dataholders;

/**
 * A wrapper for general information about the entire Household agent population at the end of a day.
 *
 * @author László Tárkányi
 *
 * @param simulationRun (int) The current simulation run in the simulation set at the time of the creation of this record.
 * @param day (int) The current day in the simulation run at the time of the creation of this record.
 * @param averageSatisfaction (double) The average satisfaction of the entire Household agent population.
 * @param averageSatisfactionStandardDeviation (double) The average standard deviation of Household satisfaction in the entire population.
 */
public record PopulationEndOfDayDataHolder(
        int simulationRun,
        Integer day,
        double averageSatisfaction,
        double averageSatisfactionStandardDeviation
) {
    // no-op
}