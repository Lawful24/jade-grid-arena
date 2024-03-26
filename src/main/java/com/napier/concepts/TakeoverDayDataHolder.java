package com.napier.concepts;

// TODO: rename this
public record TakeoverDayDataHolder(
        int simulationRun,
        Integer takeoverDay,
        double averageSatisfaction,
        double averageSatisfactionStandardDeviation
) {
}
