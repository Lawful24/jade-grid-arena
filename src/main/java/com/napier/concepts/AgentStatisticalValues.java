package com.napier.concepts;

public record AgentStatisticalValues(
        double uq,
        double lq,
        double ninetyfifth,
        double max,
        double min,
        double median
) {
    // no-op
}
