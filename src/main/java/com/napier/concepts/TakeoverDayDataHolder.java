package com.napier.concepts;

public record TakeoverDayDataHolder(
        int simulationRun,
        Integer takeoverDay,
        double averageSatisfaction,
        double averageSatisfactionStandardDeviation
) {
    // no-op
}
