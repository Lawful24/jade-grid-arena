package com.napier.concepts.dataholders;

import com.napier.concepts.AgentContact;

import java.io.Serializable;
import java.util.ArrayList;

public record EndOfDayAdvertisingBoardDataHolder(
        ArrayList<AgentContact> contacts,
        int numOfSocialAgents,
        int numOfSelfishAgents,
        double averageSocialSatisfaction,
        double averageSelfishSatisfaction,
        double averageSocialSatisfactionStandardDeviation,
        double averageSelfishSatisfactionStandardDeviation,
        AgentStatisticalValuesPerStrategyType socialStatisticalValues,
        AgentStatisticalValuesPerStrategyType selfishStatisticalValues,
        double initialRandomAllocationAverageSatisfaction,
        double optimumAveragePossibleSatisfaction
) implements Serializable {
    // no-op
}
