package com.napier.concepts.dataholders;

public record TakeoverDayDataHolder(
        int simulationRun,
        Integer takeoverDay,
        double averageSatisfaction,
        double averageSatisfactionStandardDeviation
) {
    // no-op
}